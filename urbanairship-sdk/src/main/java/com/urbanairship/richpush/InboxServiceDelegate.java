/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service delegate for the {@link RichPushUpdateService} to handle inbox updates.
 */
class InboxServiceDelegate extends BaseIntentService.Delegate {

    private static final String DELETE_MESSAGES_PATH = "api/user/%s/messages/delete/";
    private static final String MARK_READ_MESSAGES_PATH = "api/user/%s/messages/unread/";
    private static final String MESSAGES_PATH = "api/user/%s/messages/";

    private static final String DELETE_MESSAGES_KEY = "delete";
    private static final String MARK_READ_MESSAGES_KEY = "mark_as_read";
    private static final String MESSAGE_URL = "api/user/%s/messages/message/%s/";
    private static final String CHANNEL_ID_HEADER = "X-UA-Channel-ID";

    private final UAirship airship;
    private final RichPushUser user;
    private final RichPushResolver resolver;
    private final String hostUrl;
    private final RequestFactory requestFactory;


    public InboxServiceDelegate(Context context, PreferenceDataStore dataStore) {
        this(context, dataStore, new RequestFactory(), new RichPushResolver(context), UAirship.shared());
    }

    public InboxServiceDelegate(Context context, PreferenceDataStore dataStore,
                                RequestFactory requestFactory, RichPushResolver resolver, UAirship airship) {
        super(context, dataStore);

        this.requestFactory = requestFactory;
        this.resolver = resolver;
        this.airship = airship;
        this.user = airship.getInbox().getUser();
        this.hostUrl = airship.getAirshipConfigOptions().hostURL;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {

            case RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE:
                if (!RichPushUser.isCreated()) {
                    Logger.debug("InboxServiceDelegate - User has not been created, canceling messages update");
                    RichPushUpdateService.respond(intent, false);
                } else {
                    boolean success = this.updateMessages();
                    RichPushUpdateService.respond(intent, success);

                    this.syncReadMessageState();
                    this.syncDeletedMessageState();
                }
                break;

            case RichPushUpdateService.ACTION_SYNC_MESSAGE_STATE:
                this.syncReadMessageState();
                this.syncDeletedMessageState();
                break;
        }
    }

    /**
     * Update the inbox messages.
     *
     * @return <code>true</code> if messages were updated, otherwise <code>false</code>.
     */
    private boolean updateMessages() {
        Logger.info("Refreshing inbox messages.");

        URL getMessagesURL = RichPushUpdateService.getUserURL(MESSAGES_PATH, user.getId());
        if (getMessagesURL == null) {
            return false;
        }

        Logger.verbose("InboxServiceDelegate - Fetching inbox messages.");
        Response response = requestFactory.createRequest("GET", getMessagesURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .setHeader(CHANNEL_ID_HEADER, airship.getPushManager().getChannelId())
                                          .setIfModifiedSince(getDataStore().getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0))
                                          .execute();

        Logger.verbose("InboxServiceDelegate - Fetch inbox messages response: " + response);

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
                getDataStore().put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, response.getLastModifiedTime());
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
                Logger.error("InboxServiceDelegate - Invalid message payload: " + message);
                continue;
            }

            String messageId = message.getMap().opt(RichPushMessage.MESSAGE_ID_KEY).getString();
            if (messageId == null) {
                Logger.error("InboxServiceDelegate - Invalid message payload, missing message ID: " + message);
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

        URL deleteMessagesURL = RichPushUpdateService.getUserURL(DELETE_MESSAGES_PATH, user.getId());
        if (deleteMessagesURL == null) {
            return;
        }

        Logger.verbose("InboxServiceDelegate - Found " + idsToDelete.size() + " messages to delete.");

        /*
         * Note: If we can't delete the messages on the server, leave them untouched
         * and we'll get them next time.
         */
        JsonMap payload = buildMessagesPayload(DELETE_MESSAGES_KEY, idsToDelete);
        if (payload == null) {
            return;
        }

        Logger.verbose("InboxServiceDelegate - Deleting inbox messages with payload: " + payload);
        Response response = requestFactory.createRequest("POST", deleteMessagesURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setRequestBody(payload.toString(), "application/json")
                                          .setHeader(CHANNEL_ID_HEADER, airship.getPushManager().getChannelId())
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("InboxServiceDelegate - Delete inbox messages response: " + response);
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


        URL markMessagesReadURL = RichPushUpdateService.getUserURL(MARK_READ_MESSAGES_PATH, user.getId());
        if (markMessagesReadURL == null) {
            return;
        }

        Logger.verbose("InboxServiceDelegate - Found " + idsToUpdate.size() + " messages to mark read.");

        /*
         * Note: If we can't mark the messages read on the server, leave them untouched
         * and we'll get them next time.
         */
        JsonMap payload = buildMessagesPayload(MARK_READ_MESSAGES_KEY, idsToUpdate);
        if (payload == null) {
            return;
        }

        Logger.verbose("InboxServiceDelegate - Marking inbox messages read request with payload: " + payload);
        Response response = requestFactory.createRequest("POST", markMessagesReadURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setRequestBody(payload.toString(), "application/json")
                                          .setHeader(CHANNEL_ID_HEADER, airship.getPushManager().getChannelId())
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("InboxServiceDelegate - Mark inbox messages read response: " + response);

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
}
