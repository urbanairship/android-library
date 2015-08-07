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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service delegate for the {@link RichPushUpdateService} to handle inbox updates.
 */
class InboxServiceDelegate extends BaseIntentService.Delegate {

    private static final String DELETE_MESSAGES_KEY = "delete";
    private static final String MARK_READ_MESSAGES_KEY = "mark_as_read";
    private static final String MESSAGE_URL = "api/user/%s/messages/message/%s/";

    private final UserAPIClient userClient;
    private final UAirship airship;
    private final RichPushUser user;
    private final RichPushResolver resolver;
    private final String hostUrl;

    public InboxServiceDelegate(Context context, PreferenceDataStore dataStore) {
        this(context, dataStore, new UserAPIClient(), new RichPushResolver(context), UAirship.shared());
    }

    public InboxServiceDelegate(Context context, PreferenceDataStore dataStore,
                                UserAPIClient userClient, RichPushResolver resolver, UAirship airship) {
        super(context, dataStore);

        this.userClient = userClient;
        this.resolver = resolver;
        this.airship = airship;
        this.user = airship.getRichPushManager().getRichPushUser();
        this.hostUrl = airship.getAirshipConfigOptions().hostURL;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE.equals(intent.getAction())) {
            return;
        }

        if (!RichPushUser.isCreated()) {
            Logger.debug("InboxServiceDelegate - User has not been created, canceling messages update");
            RichPushUpdateService.respond(intent, false);
        } else {
            boolean success = this.updateMessages();
            RichPushUpdateService.respond(intent, success);

            // Do clean up
            this.handleReadMessages();
            this.handleDeletedMessages();
        }
    }

    /**
     * Update the inbox messages.
     *
     * @return <code>true</code> if messages were updated, otherwise <code>false</code>.
     */
    private boolean updateMessages() {
        MessageListResponse response = userClient.getMessages(user.getId(),
                user.getPassword(),
                getDataStore().getLong(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, 0));

        Logger.info("Refreshing inbox messages.");

        if (response == null) {
            Logger.debug("InboxServiceDelegate - Inbox message list request failed.");
            return false;
        }

        Logger.debug("InboxServiceDelegate - Inbox message list request received: " + response.getStatus());

        switch (response.getStatus()) {
            case HttpURLConnection.HTTP_NOT_MODIFIED:
                Logger.info("Inbox messages already up-to-date. ");
                return true;

            case HttpURLConnection.HTTP_OK:
                ContentValues[] serverMessages = response.getServerMessages();
                if (serverMessages == null) {
                    Logger.info("Inbox message list is empty.");
                } else {
                    Logger.info("Received " + serverMessages.length + " inbox messages.");
                    updateInbox(serverMessages);
                    getDataStore().put(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME, response.getLastModifiedTimeMS());
                }
                return true;

            default:
                Logger.info("Unable to update inbox messages.");
                return false;
        }
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
        Set<String> allIds = getMessageIdsFromCursor(resolver.getAllMessages());
        if (allIds != null) {
            allIds.removeAll(serverMessageIds);
            airship.getRichPushManager().getRichPushInbox().deleteMessages(allIds);
        }

        // update the inbox cache
        airship.getRichPushManager().getRichPushInbox().updateCache();
    }

    /**
     * Handle deletion of messages.
     */
    private void handleDeletedMessages() {
        Set<String> idsToDelete = getMessageIdsFromCursor(resolver.getDeletedMessages());

        if (idsToDelete != null && idsToDelete.size() > 0) {
            Logger.verbose("InboxServiceDelegate - Found " + idsToDelete.size() + " messages to delete.");

            /*
             * Note: If we can't delete the messages on the server, leave them untouched
             * and we'll get them next time.
             */
            JSONObject payload = buildMessagesPayload(DELETE_MESSAGES_KEY, idsToDelete);
            if (userClient.deleteMessages(payload, user.getId(), user.getPassword())) {
                resolver.deleteMessages(idsToDelete);
            }
        }
    }

    /**
     * Handle marking messages read.
     */
    private void handleReadMessages() {
        Set<String> idsToUpdate = getMessageIdsFromCursor(resolver.getReadUpdatedMessages());

        if (idsToUpdate != null && idsToUpdate.size() > 0) {
            Logger.verbose("InboxServiceDelegate - Found " + idsToUpdate.size() + " messages to mark read.");

            /*
             * Note: If we can't mark the messages read on the server, leave them untouched
             * and we'll get them next time.
             */
            JSONObject payload = buildMessagesPayload(MARK_READ_MESSAGES_KEY, idsToUpdate);
            if (userClient.markMessagesRead(payload, user.getId(), user.getPassword())) {
                ContentValues values = new ContentValues();
                values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, 0);
                resolver.updateMessages(idsToUpdate, values);
            }
        }
    }

    /**
     * Get the message IDs.
     *
     * @param cursor The cursor to get the message IDs from.
     * @return The message IDs as a set of strings.
     */
    private Set<String> getMessageIdsFromCursor(Cursor cursor) {
        if (cursor == null) {
            return null;
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
}
