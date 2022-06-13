/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Job handler for {@link Inbox} component.
 */
class InboxJobHandler {

    /**
     * Starts the service in order to update just the {@link Message}'s messages.
     */
    static final String ACTION_RICH_PUSH_MESSAGES_UPDATE = "ACTION_RICH_PUSH_MESSAGES_UPDATE";

    /**
     * Starts the service to sync message state.
     */
    static final String ACTION_SYNC_MESSAGE_STATE = "ACTION_SYNC_MESSAGE_STATE";

    /**
     * Starts the service in order to update just the {@link User} itself.
     */
    static final String ACTION_RICH_PUSH_USER_UPDATE = "ACTION_RICH_PUSH_USER_UPDATE";

    /**
     * Extra key to indicate if the rich push user needs to be updated forcefully.
     */
    static final String EXTRA_FORCEFULLY = "EXTRA_FORCEFULLY";

    static final String LAST_MESSAGE_REFRESH_TIME = "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME";

    private static final String LAST_UPDATE_TIME = "com.urbanairship.user.LAST_UPDATE_TIME";
    private static final long USER_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    private final MessageDao messageDao;
    private final User user;
    private final Inbox inbox;
    private final PreferenceDataStore dataStore;
    private final AirshipChannel channel;

    private final InboxApiClient inboxApiClient;

    InboxJobHandler(@NonNull Context context,
                    @NonNull Inbox inbox,
                    @NonNull User user,
                    @NonNull AirshipChannel channel,
                    @NonNull AirshipRuntimeConfig runtimeConfig,
                    @NonNull PreferenceDataStore dataStore,
                    @NonNull MessageDao messageDao) {
        this(inbox, user, channel, dataStore, messageDao, new InboxApiClient(runtimeConfig));
    }

    @VisibleForTesting
    InboxJobHandler(@NonNull Inbox inbox,
                    @NonNull User user,
                    @NonNull AirshipChannel channel,
                    @NonNull PreferenceDataStore dataStore,
                    @NonNull MessageDao messageDao,
                    @NonNull InboxApiClient inboxApiClient) {
        this.inbox = inbox;
        this.user = user;
        this.channel = channel;
        this.dataStore = dataStore;
        this.messageDao = messageDao;
        this.inboxApiClient = inboxApiClient;
    }

    /**
     * Delete saved state from the data store.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void removeStoredData() {
        dataStore.remove(LAST_MESSAGE_REFRESH_TIME);
        dataStore.remove(LAST_UPDATE_TIME);
    }

    /**
     * Called to handle jobs from {@link Inbox#onPerformJob(UAirship, JobInfo)}.
     *
     * @param jobInfo The airship jobInfo.
     * @return The job result.
     */
    @NonNull
    JobResult performJob(@NonNull JobInfo jobInfo) {
        switch (jobInfo.getAction()) {
            case ACTION_RICH_PUSH_USER_UPDATE:
                onUpdateUser(jobInfo.getExtras().opt(EXTRA_FORCEFULLY).getBoolean(false));
                break;

            case ACTION_RICH_PUSH_MESSAGES_UPDATE:
                onUpdateMessages();
                break;

            case ACTION_SYNC_MESSAGE_STATE:
                onSyncMessages();
                break;
        }

        return JobResult.SUCCESS;
    }

    /**
     * Updates the message list.
     */
    private void onUpdateMessages() {
        if (!user.isUserCreated()) {
            Logger.debug("User has not been created, canceling messages update");
            inbox.onUpdateMessagesFinished(false);
        } else {
            boolean success = this.updateMessages();
            inbox.refresh(true);
            inbox.onUpdateMessagesFinished(success);
            this.syncReadMessageState();
            this.syncDeletedMessageState();
        }
    }

    /**
     * Sync message sate.
     */
    private void onSyncMessages() {
        this.syncReadMessageState();
        this.syncDeletedMessageState();
    }

    /**
     * Updates the rich push user.
     *
     * @param forcefully If the user should be updated even if its been recently updated.
     */
    private void onUpdateUser(boolean forcefully) {
        if (!forcefully) {
            long lastUpdateTime = dataStore.getLong(LAST_UPDATE_TIME, 0);
            long now = System.currentTimeMillis();
            if (!(lastUpdateTime > now || (lastUpdateTime + USER_UPDATE_INTERVAL_MS) < now)) {
                // Not ready to update
                return;
            }
        }

        boolean success;
        if (!user.isUserCreated()) {
            success = this.createUser();
        } else {
            success = this.updateUser();
        }

        user.onUserUpdated(success);
    }

    /**
     * Update the inbox messages.
     *
     * @return <code>true</code> if messages were updated, otherwise <code>false</code>.
     */
    private boolean updateMessages() {
        Logger.info("Refreshing inbox messages.");

        String channelId = channel.getId();
        if (UAStringUtil.isEmpty(channelId)) {
            Logger.verbose("The channel ID does not exist.");
            return false;
        }

        Logger.verbose("Fetching inbox messages.");

        try {
            Response<JsonList> response = inboxApiClient.fetchMessages(user, channelId, dataStore.getLong(LAST_MESSAGE_REFRESH_TIME, 0));

            Logger.verbose("Fetch inbox messages response: %s", response);

            // 200-299
            if (response.isSuccessful()) {
                JsonList result = response.getResult();
                Logger.info("InboxJobHandler - Received %s inbox messages.", response.getResult().size());
                updateInbox(response.getResult());
                dataStore.put(LAST_MESSAGE_REFRESH_TIME, response.getLastModifiedTime());
                return true;
            }

            // 304
            if (response.getStatus() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Logger.debug("Inbox messages already up-to-date. ");
                return true;
            }

            Logger.debug("Unable to update inbox messages %s.", response);
            return false;

        } catch (RequestException e) {
            Logger.debug(e, "Update Messages failed.");
            return false;
        }
    }

    /**
     * Update the Rich Push Inbox.
     *
     * @param serverMessages The messages from the server.
     */
    private void updateInbox(JsonList serverMessages) {
        List<JsonValue> messagesToInsert = new ArrayList<>();
        HashSet<String> serverMessageIds = new HashSet<>();

        for (JsonValue message : serverMessages) {
            if (!message.isJsonMap()) {
                Logger.error("InboxJobHandler - Invalid message payload: %s", message);
                continue;
            }

            String messageId = message.optMap().opt(Message.MESSAGE_ID_KEY).getString();
            if (messageId == null) {
                Logger.error("InboxJobHandler - Invalid message payload, missing message ID: %s", message);
                continue;
            }

            serverMessageIds.add(messageId);

            MessageEntity messageEntity = MessageEntity.createMessageFromPayload(messageId, message);

            if (messageEntity == null) {
                Logger.error("InboxJobHandler - Message Entity is null");
                continue;
            }

            if (!messageDao.messageExists(messageEntity.messageId)) {
                messagesToInsert.add(message);
            }
        }

        // Bulk insert any new messages
        if (messagesToInsert.size() > 0) {
            messageDao.insertMessages(MessageEntity.createMessagesFromPayload(messagesToInsert));
        }

        List<String> deletedMessageIds = messageDao.getMessageIds();
        deletedMessageIds.removeAll(serverMessageIds);
        messageDao.deleteMessages(deletedMessageIds);
    }

    /**
     * Synchronizes local deleted message state with the server.
     */
    private void syncDeletedMessageState() {
        String channelId = channel.getId();
        if (UAStringUtil.isEmpty(channelId)) {
            return;
        }

        Collection<MessageEntity> messagesToUpdate = messageDao.getLocallyDeletedMessages();
        List<String> idsToDelete = new ArrayList<>();
        List<JsonValue> reportings = new ArrayList<>();
        for (MessageEntity message : messagesToUpdate) {
            if (message.getMessageReporting() != null) {
                reportings.add(message.getMessageReporting());
                idsToDelete.add(message.getMessageId());
            }
        }

        if (idsToDelete.size() == 0) {
            // nothing to do
            return;
        }

        Logger.verbose("Found %s messages to delete.", idsToDelete.size());

        try {
            Response<Void> response = inboxApiClient.syncDeletedMessageState(user, channelId, reportings);
            Logger.verbose("Delete inbox messages response: %s", response);

            if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                messageDao.deleteMessages(idsToDelete);
            }
        } catch (RequestException e) {
            Logger.debug(e, "Deleted message state synchronize failed.");
        }
    }

    /**
     * Synchronizes local read messages state with the server.
     */
    private void syncReadMessageState() {
        String channelId = channel.getId();
        if (UAStringUtil.isEmpty(channelId)) {
            return;
        }

        Collection<MessageEntity> messagesToUpdate = messageDao.getLocallyReadMessages();
        List<String> idsToUpdate = new ArrayList<>();
        List<JsonValue> reportings = new ArrayList<>();
        for (MessageEntity message : messagesToUpdate) {
            if (message.getMessageReporting() != null) {
                reportings.add(message.getMessageReporting());
                idsToUpdate.add(message.getMessageId());
            }
        }

        if (idsToUpdate.isEmpty()) {
            return;
        }

        Logger.verbose("Found %s messages to mark read.", idsToUpdate.size());

        try {
            Response<Void> response = inboxApiClient.syncReadMessageState(user, channelId, reportings);
            Logger.verbose("Mark inbox messages read response: %s", response);

            if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                messageDao.markMessagesReadOrigin(idsToUpdate);
            }
        } catch (RequestException e) {
            Logger.debug(e, "Read message state synchronize failed.");
        }
    }

    /**
     * Create the user.
     *
     * @return <code>true</code> if user was created, otherwise <code>false</code>.
     */
    private boolean createUser() {
        String channelId = channel.getId();
        if (UAStringUtil.isEmpty(channelId)) {
            Logger.debug("No Channel. User will be created after channel registrations finishes.");
            return false;
        }

        try {
            Response<UserCredentials> response = inboxApiClient.createUser(channelId);

            // 200-209
            if (response.isSuccessful()) {
                UserCredentials userCredentials = response.getResult();

                Logger.info("InboxJobHandler - Created Rich Push user: %s", userCredentials.getUsername());
                dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis());
                dataStore.remove(LAST_MESSAGE_REFRESH_TIME);
                user.onCreated(userCredentials.getUsername(), userCredentials.getPassword(), channelId);
                return true;
            }

            Logger.debug("Rich Push user creation failed: %s", response);
            return false;

        } catch (RequestException e) {
            Logger.debug(e, "User creation failed.");
            return false;
        }
    }

    /**
     * Update the user.
     * <p>
     * If the update returns a {@code 401: NOT AUTHORIZED} response, re-creation of the {@link User}
     * will be attempted via {@link #createUser()}.
     *
     * @return <code>true</code> if user was updated, otherwise <code>false</code>.
     */
    private boolean updateUser() {
        String channelId = channel.getId();

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.debug("No Channel. Skipping Rich Push user update.");
            return false;
        }

        try {
            Response<Void> response = inboxApiClient.updateUser(user, channelId);
            Logger.verbose("Update Rich Push user response: %s", response);

            int status = response.getStatus();
            if (status == HttpURLConnection.HTTP_OK) {
                Logger.info("Rich Push user updated.");
                dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis());
                user.onUpdated(channelId);
                return true;
            } else if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Logger.debug("Re-creating Rich Push user.");
                dataStore.put(LAST_UPDATE_TIME, 0);
                return createUser();
            }

            dataStore.put(LAST_UPDATE_TIME, 0);
            return false;

        } catch (RequestException e) {
            Logger.debug(e, "User update failed.");
            return false;
        }
    }

}
