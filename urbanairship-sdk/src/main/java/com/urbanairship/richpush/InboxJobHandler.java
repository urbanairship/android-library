/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Job handler for {@link RichPushInbox} component.
 */
class InboxJobHandler {

    /**
     * Starts the service in order to update just the {@link RichPushMessage}'s messages.
     */
    static final String ACTION_RICH_PUSH_MESSAGES_UPDATE = "ACTION_RICH_PUSH_MESSAGES_UPDATE";

    /**
     * Starts the service to sync message state.
     */
    static final String ACTION_SYNC_MESSAGE_STATE = "ACTION_SYNC_MESSAGE_STATE";

    /**
     * Starts the service in order to update just the {@link RichPushUser} itself.
     */
    static final String ACTION_RICH_PUSH_USER_UPDATE = "ACTION_RICH_PUSH_USER_UPDATE";

    /**
     * Extra key to indicate if the rich push user needs to be updated forcefully.
     */
    static final String EXTRA_FORCEFULLY = "EXTRA_FORCEFULLY";


    static final String LAST_MESSAGE_REFRESH_TIME = "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME";

    private static final String DELETE_MESSAGES_PATH = "api/user/%s/messages/delete/";
    private static final String MARK_READ_MESSAGES_PATH = "api/user/%s/messages/unread/";
    private static final String MESSAGES_PATH = "api/user/%s/messages/";

    private static final String DELETE_MESSAGES_KEY = "delete";
    private static final String MARK_READ_MESSAGES_KEY = "mark_as_read";
    private static final String MESSAGE_URL = "api/user/%s/messages/message/%s/";
    private static final String CHANNEL_ID_HEADER = "X-UA-Channel-ID";

    private static final String USER_CREATION_PATH = "api/user/";
    private static final String USER_UPDATE_PATH = "api/user/%s/";

    private static final String LAST_UPDATE_TIME = "com.urbanairship.user.LAST_UPDATE_TIME";
    private static final long USER_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    private static final String PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels";
    private static final String PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels";
    private static final String PAYLOAD_ADD_KEY = "add";

    private final RichPushResolver resolver;
    private final String hostUrl;
    private final RichPushUser user;
    private final RequestFactory requestFactory;
    private final PreferenceDataStore dataStore;
    private final UAirship airship;

    InboxJobHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(airship, dataStore, new RequestFactory(), new RichPushResolver(context));
    }

    @VisibleForTesting
    InboxJobHandler(UAirship airship, PreferenceDataStore dataStore,
                    RequestFactory requestFactory, RichPushResolver resolver) {
        this.dataStore = dataStore;
        this.requestFactory = requestFactory;
        this.resolver = resolver;
        this.airship = airship;
        this.user = airship.getInbox().getUser();
        this.hostUrl = airship.getAirshipConfigOptions().hostURL;
    }

    /**
     * Called to handle jobs from {@link RichPushInbox#onPerformJob(UAirship, JobInfo)}.
     *
     * @param jobInfo The airship jobInfo.
     * @return The job result.
     */
    @JobInfo.JobResult
    int performJob(JobInfo jobInfo) {
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

        return JobInfo.JOB_FINISHED;
    }

    /**
     * Updates the message list.
     */
    private void onUpdateMessages() {
        if (!RichPushUser.isCreated()) {
            Logger.debug("InboxJobHandler - User has not been created, canceling messages update");
            airship.getInbox().onUpdateMessagesFinished(false);
        } else {
            boolean success = this.updateMessages();
            airship.getInbox().onUpdateMessagesFinished(success);
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
        if (!RichPushUser.isCreated()) {
            success = this.createUser();
        } else {
            success = this.updateUser();
        }

        airship.getInbox().getUser().onUserUpdated(success);
    }

    /**
     * Update the inbox messages.
     *
     * @return <code>true</code> if messages were updated, otherwise <code>false</code>.
     */
    private boolean updateMessages() {
        Logger.info("Refreshing inbox messages.");

        URL getMessagesURL = getUserURL(MESSAGES_PATH, user.getId());
        if (getMessagesURL == null) {
            return false;
        }

        Logger.verbose("InboxJobHandler - Fetching inbox messages.");
        Response response = requestFactory.createRequest("GET", getMessagesURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .setHeader(CHANNEL_ID_HEADER, airship.getPushManager().getChannelId())
                                          .setIfModifiedSince(dataStore.getLong(LAST_MESSAGE_REFRESH_TIME, 0))
                                          .execute();

        Logger.verbose("InboxJobHandler - Fetch inbox messages response: " + response);

        int status = response == null ? -1 : response.getStatus();

        // 304
        if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            Logger.info("Inbox messages already up-to-date. ");
            return true;
        }

        // 200
        if (status == HttpURLConnection.HTTP_OK) {
            JsonList serverMessages = null;
            try {
                JsonMap responseJson = JsonValue.parseString(response.getResponseBody()).getMap();
                if (responseJson != null) {
                    serverMessages = responseJson.get("messages").getList();
                }
            } catch (JsonException e) {
                Logger.error("Failed to update inbox. Unable to parse response body: " + response.getResponseBody());
                return false;
            }

            if (serverMessages == null) {
                Logger.info("Inbox message list is empty.");
            } else {
                Logger.info("Received " + serverMessages.size() + " inbox messages.");
                updateInbox(serverMessages);
                dataStore.put(LAST_MESSAGE_REFRESH_TIME, response.getLastModifiedTime());
            }

            return true;
        }

        Logger.info("Unable to update inbox messages.");
        return false;
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
                Logger.error("InboxJobHandler - Invalid message payload: " + message);
                continue;
            }

            String messageId = message.getMap().opt(RichPushMessage.MESSAGE_ID_KEY).getString();
            if (messageId == null) {
                Logger.error("InboxJobHandler - Invalid message payload, missing message ID: " + message);
                continue;
            }

            serverMessageIds.add(messageId);

            if (resolver.updateMessage(messageId, message) != 1) {
                messagesToInsert.add(message);
            }
        }

        // Bulk insert any new messages
        if (messagesToInsert.size() > 0) {
            resolver.insertMessages(messagesToInsert);
        }

        // Delete any messages that did not come down with the message list
        Set<String> deletedMessageIds = resolver.getMessageIds();
        deletedMessageIds.removeAll(serverMessageIds);
        resolver.deleteMessages(deletedMessageIds);

        // update the inbox cache
        airship.getInbox().refresh(true);
    }

    /**
     * Synchronizes local deleted message state with the server.
     */
    private void syncDeletedMessageState() {
        Set<String> idsToDelete = resolver.getDeletedMessageIds();

        if (idsToDelete.size() == 0) {
            // nothing to do
            return;
        }

        URL deleteMessagesURL = getUserURL(DELETE_MESSAGES_PATH, user.getId());
        if (deleteMessagesURL == null) {
            return;
        }

        Logger.verbose("InboxJobHandler - Found " + idsToDelete.size() + " messages to delete.");

        /*
         * Note: If we can't delete the messages on the server, leave them untouched
         * and we'll get them next time.
         */
        JsonMap payload = buildMessagesPayload(DELETE_MESSAGES_KEY, idsToDelete);
        if (payload == null) {
            return;
        }

        Logger.verbose("InboxJobHandler - Deleting inbox messages with payload: " + payload);
        Response response = requestFactory.createRequest("POST", deleteMessagesURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setRequestBody(payload.toString(), "application/json")
                                          .setHeader(CHANNEL_ID_HEADER, airship.getPushManager().getChannelId())
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("InboxJobHandler - Delete inbox messages response: " + response);
        if (response != null && response.getStatus() == HttpURLConnection.HTTP_OK) {
            resolver.deleteMessages(idsToDelete);
        }
    }

    /**
     * Synchronizes local read messages state with the server.
     */
    private void syncReadMessageState() {
        Set<String> idsToUpdate = resolver.getReadUpdatedMessageIds();

        if (idsToUpdate.size() == 0) {
            // nothing to do
            return;
        }


        URL markMessagesReadURL = getUserURL(MARK_READ_MESSAGES_PATH, user.getId());
        if (markMessagesReadURL == null) {
            return;
        }

        Logger.verbose("InboxJobHandler - Found " + idsToUpdate.size() + " messages to mark read.");

        /*
         * Note: If we can't mark the messages read on the server, leave them untouched
         * and we'll get them next time.
         */
        JsonMap payload = buildMessagesPayload(MARK_READ_MESSAGES_KEY, idsToUpdate);
        if (payload == null) {
            return;
        }

        Logger.verbose("InboxJobHandler - Marking inbox messages read request with payload: " + payload);
        Response response = requestFactory.createRequest("POST", markMessagesReadURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setRequestBody(payload.toString(), "application/json")
                                          .setHeader(CHANNEL_ID_HEADER, airship.getPushManager().getChannelId())
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("InboxJobHandler - Mark inbox messages read response: " + response);

        if (response != null && response.getStatus() == HttpURLConnection.HTTP_OK) {
            resolver.markMessagesReadOrigin(idsToUpdate);
        }
    }

    /**
     * Builds the message payload.
     *
     * @param root String root of payload.
     * @param ids Set of message ID strings.
     * @return A message payload as a JsonMap.
     */
    private JsonMap buildMessagesPayload(@NonNull String root, @NonNull Set<String> ids) {
        List<String> urls = new ArrayList<>();
        String userId = this.user.getId();
        for (String id : ids) {
            String url = hostUrl + String.format(MESSAGE_URL, userId, id);
            urls.add(url);
        }

        JsonMap payload = JsonMap.newBuilder()
                                 .put(root, JsonValue.wrapOpt(urls))
                                 .build();

        Logger.verbose(payload.toString());
        return payload;
    }


    /**
     * Create the user.
     *
     * @return <code>true</code> if user was created, otherwise <code>false</code>.
     */
    private boolean createUser() {
        String channelId = airship.getPushManager().getChannelId();
        if (UAStringUtil.isEmpty(channelId)) {
            Logger.debug("InboxJobHandler - No Channel. User will be created after channel registrations finishes.");
            return false;
        }

        URL userCreationURL = getUserURL(USER_CREATION_PATH);
        if (userCreationURL == null) {
            return false;
        }

        String payload = createNewUserPayload(channelId);
        Logger.verbose("InboxJobHandler - Creating Rich Push user with payload: " + payload);
        Response response = requestFactory.createRequest("POST", userCreationURL)
                                          .setCredentials(airship.getAirshipConfigOptions().getAppKey(), airship.getAirshipConfigOptions().getAppSecret())
                                          .setRequestBody(payload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        // Check for failure
        if (response == null || response.getStatus() != HttpURLConnection.HTTP_CREATED) {
            Logger.verbose("InboxJobHandler - Rich Push user creation failed: " + response);
            return false;
        }

        String userId = null;
        String userToken = null;

        try {
            JsonMap credentials = JsonValue.parseString(response.getResponseBody()).getMap();
            if (credentials != null) {
                userId = credentials.get("user_id").getString();
                userToken = credentials.get("password").getString();
            }
        } catch (JsonException ex) {
            Logger.error("InboxJobHandler - Unable to parse Rich Push user response: " + response);
            return false;
        }

        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
            Logger.error("InboxJobHandler - Rich Push user creation failed: " + response);
            return false;
        }

        Logger.info("Created Rich Push user: " + userId);
        dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis());
        dataStore.remove(LAST_MESSAGE_REFRESH_TIME);
        user.setUser(userId, userToken);

        return true;
    }

    /**
     * Update the user.
     *
     * @return <code>true</code> if user was updated, otherwise <code>false</code>.
     */
    private boolean updateUser() {
        String channelId = airship.getPushManager().getChannelId();

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.debug("InboxJobHandler - No Channel. Skipping Rich Push user update.");
            return false;
        }


        URL userUpdateURL = getUserURL(USER_UPDATE_PATH, user.getId());
        if (userUpdateURL == null) {
            return false;
        }

        String payload = createUpdateUserPayload(channelId);
        Logger.verbose("InboxJobHandler - Updating user with payload: " + payload);
        Response response = requestFactory.createRequest("POST", userUpdateURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setRequestBody(payload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("InboxJobHandler - Update Rich Push user response: " + response);
        if (response != null && response.getStatus() == HttpURLConnection.HTTP_OK) {
            Logger.info("Rich Push user updated.");
            dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis());
            return true;
        }

        dataStore.put(LAST_UPDATE_TIME, 0);
        return false;
    }

    /**
     * Create the new user payload.
     *
     * @return The user payload as a JSON object.
     */
    private String createNewUserPayload(@NonNull String channelId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(getPayloadChannelsKey(), Collections.singletonList(channelId));
        return JsonValue.wrapOpt(payload).toString();
    }

    /**
     * Create the user update payload.
     *
     * @return The user payload as a JSON object.
     */
    private String createUpdateUserPayload(@NonNull String channelId) {
        Map<String, Object> addChannels = new HashMap<>();
        addChannels.put(PAYLOAD_ADD_KEY, Collections.singletonList(channelId));

        Map<String, Object> payload = new HashMap<>();
        payload.put(getPayloadChannelsKey(), addChannels);

        return JsonValue.wrapOpt(payload).toString();
    }

    /**
     * Get the payload channels key based on the platform.
     *
     * @return The payload channels key as a string.
     */
    private String getPayloadChannelsKey() {
        if (airship.getPlatformType() == UAirship.AMAZON_PLATFORM) {
            return PAYLOAD_AMAZON_CHANNELS_KEY;
        } else {
            return PAYLOAD_ANDROID_CHANNELS_KEY;
        }
    }


    /**
     * Gets the URL for inbox/user api calls
     *
     * @param path The url path.
     * @param args Url arguments.
     * @return The URL or null if an error occurred.
     */
    private URL getUserURL(String path, Object... args) {
        String hostURL = airship.getAirshipConfigOptions().hostURL;
        String urlString = String.format(hostURL + path, args);
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid userURL", e);
        }
        return null;
    }
}
