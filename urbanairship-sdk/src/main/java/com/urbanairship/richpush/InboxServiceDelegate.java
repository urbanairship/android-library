/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.richpush;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.RichPushTable;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        this.user = airship.getRichPushManager().getRichPushUser();
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
            ContentValues[] serverMessages;
            try {
                serverMessages = messagesFromResponse(response.getResponseBody());
            } catch (JsonException e) {
                Logger.error("Failed to update inbox. Unable to parse response body: " + response.getResponseBody());
                return false;
            }

            if (serverMessages == null) {
                Logger.info("Inbox message list is empty.");
            } else {
                Logger.info("Received " + serverMessages.length + " inbox messages.");
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
    private void updateInbox(ContentValues[] serverMessages) {
        List<ContentValues> messagesToInsert = new ArrayList<>();
        HashSet<String> serverMessageIds = new HashSet<>();

        for (ContentValues message : serverMessages) {
            String messageId = message.getAsString("message_id");
            serverMessageIds.add(messageId);

            if (resolver.updateMessage(messageId, message) != 1) {
                messagesToInsert.add(message);
            }
        }

        // Bulk insert any new messages
        if (messagesToInsert.size() > 0) {
            ContentValues[] messageArray = new ContentValues[messagesToInsert.size()];
            messagesToInsert.toArray(messageArray);
            resolver.insertMessages(messageArray);
        }

        // Delete any messages that did not come down with the message list
        Set<String> deletedMessageIds = getMessageIdsFromCursor(resolver.getAllMessages());
        deletedMessageIds.removeAll(serverMessageIds);
        resolver.deleteMessages(deletedMessageIds);

        // update the inbox cache
        airship.getRichPushManager().getRichPushInbox().refresh();
    }

    /**
     * Synchronizes local deleted message state with the server.
     */
    private void syncDeletedMessageState() {
        Set<String> idsToDelete = getMessageIdsFromCursor(resolver.getDeletedMessages());

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
        JSONObject payload = buildMessagesPayload(DELETE_MESSAGES_KEY, idsToDelete);
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
        Set<String> idsToUpdate = getMessageIdsFromCursor(resolver.getReadUpdatedMessages());

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
        JSONObject payload = buildMessagesPayload(MARK_READ_MESSAGES_KEY, idsToUpdate);
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
            ContentValues values = new ContentValues();
            values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, 0);
            resolver.updateMessages(idsToUpdate, values);
        }
    }

    /**
     * Get the message IDs.
     *
     * @param cursor The cursor to get the message IDs from.
     * @return The message IDs as a set of strings.
     */
    @NonNull
    private Set<String> getMessageIdsFromCursor(Cursor cursor) {
        if (cursor == null) {
            return new HashSet<>();
        }

        Set<String> ids = new HashSet<>(cursor.getCount());

        int messageIdIndex = -1;
        while (cursor.moveToNext()) {
            if (messageIdIndex == -1) {
                messageIdIndex = cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID);
            }
            ids.add(cursor.getString(messageIdIndex));
        }

        cursor.close();

        return ids;
    }

    /**
     * Builds the message payload.
     *
     * @param root String root of payload.
     * @param ids Set of message ID strings.
     * @return A message payload as a JSONObject.
     */
    private JSONObject buildMessagesPayload(@NonNull String root, @NonNull Set<String> ids) {
        try {
            JSONObject payload = new JSONObject();
            payload.put(root, new JSONArray());
            String userId = this.user.getId();
            for (String id : ids) {
                String url = hostUrl + String.format(MESSAGE_URL, userId, id);
                payload.accumulate(root, url);
            }
            Logger.verbose(payload.toString());
            return payload;
        } catch (JSONException e) {
            Logger.info(e.getMessage());
        }
        return null;
    }


    private ContentValues[] messagesFromResponse(String messagesString) throws JsonException {
        if (UAStringUtil.isEmpty(messagesString)) {
            return null;
        }

        JsonValue messagePayload = JsonValue.parseString(messagesString);
        if (!messagePayload.isJsonMap()) {
            Logger.error("InboxServiceDelegate - Unexpected message list: " + messagesString);
            return null;
        }

        JsonList messageList = messagePayload.getMap().get("messages").getList();
        if (messageList == null) {
            Logger.error("InboxServiceDelegate - Unexpected message list: " + messagesString);
            return null;
        }

        ContentValues[] messages = new ContentValues[messageList.size()];
        for (int i = 0; i < messageList.size(); i++) {

            if (!messageList.get(i).isJsonMap()) {
                Logger.error("InboxServiceDelegate - Unexpected message payload: " + messageList.get(i));
                continue;
            }

            JsonMap messageMap = messageList.get(i).getMap();

            ContentValues values = new ContentValues();
            values.put(RichPushTable.COLUMN_NAME_TIMESTAMP, messageMap.opt("message_sent").getString());
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_ID, messageMap.opt("message_id").getString());
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_URL, messageMap.opt("message_url").getString());
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL, messageMap.opt("message_body_url").getString());
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_READ_URL, messageMap.opt("message_read_url").getString());
            values.put(RichPushTable.COLUMN_NAME_TITLE, messageMap.opt("title").getString());
            values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, messageMap.opt("unread").getBoolean(true));

            values.put(RichPushTable.COLUMN_NAME_EXTRA, messageMap.opt("extra").toString());
            values.put(RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT, messageMap.toString());

            if (messageMap.containsKey("message_expiry")) {
                values.put(RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP, messageMap.opt("message_expiry").getString());
            }

            messages[i] = values;
        }

        return messages;
    }
}
