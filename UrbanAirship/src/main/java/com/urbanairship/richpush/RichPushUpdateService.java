/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.RichPushTable;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for updating the {@link RichPushUser} and their messages.
 */
public class RichPushUpdateService extends IntentService {

    /**
     * Starts the service in order to update just the {@link RichPushMessage}'s messages.
     */
    public static final String ACTION_RICH_PUSH_MESSAGES_UPDATE = "com.urbanairship.richpush.MESSAGES_UPDATE";

    /**
     * Starts the service in order to update just the {@link RichPushUser} itself.
     */
    public static final String ACTION_RICH_PUSH_USER_UPDATE = "com.urbanairship.richpush.USER_UPDATE";

    /**
     * Extra key for a result receiver passed in with the intent.
     */
    public static final String EXTRA_RICH_PUSH_RESULT_RECEIVER = "com.urbanairship.richpush.RESULT_RECEIVER";

    /**
     * Status code indicating an update complete successfully.
     */
    public static final int STATUS_RICH_PUSH_UPDATE_SUCCESS = 0;

    /**
     * Status code indicating an update didn't not complete successfully.
     */
    public static final int STATUS_RICH_PUSH_UPDATE_ERROR = 1;

    private static final String DELETE_MESSAGES_KEY = "delete";
    private static final String MARK_READ_MESSAGES_KEY = "mark_as_read";
    private static final String MESSAGE_URL = "api/user/%s/messages/message/%s/";

    private static final String PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels";
    private static final String PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels";
    private static final String PAYLOAD_ADD_KEY = "add";

    UserAPIClient userClient;
    RichPushResolver resolver;

    public RichPushUpdateService() {
        super("RichPushUpdateService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());

        this.userClient = new UserAPIClient();
        this.resolver = new RichPushResolver(getApplicationContext());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        Logger.verbose("RichPushUpdateService - Received intent: " + action);

        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RICH_PUSH_RESULT_RECEIVER);

        if (ACTION_RICH_PUSH_MESSAGES_UPDATE.equals(action)) {
            //note - this should probably send an error result back
            if (!RichPushUser.isCreated()) {
                Logger.debug("RichPushUpdateService - User has not been created, canceling messages update");
                respond(receiver, false);
            } else {
                messagesUpdate(receiver);
            }

        } else if (ACTION_RICH_PUSH_USER_UPDATE.equals(action)) {
            userUpdate(receiver);
        }
    }

    /**
     * Deliver a result to the receiver with a bundle.
     *
     * @param receiver The result receiver.
     * @param status A boolean indicating whether rich push update succeeded.
     * @param bundle The bundle delivered to the receiver.
     */
    private void respond(ResultReceiver receiver, boolean status, Bundle bundle) {
        if (receiver != null) {
            if (bundle == null) {
                bundle = new Bundle();
            }
            if (status) {
                receiver.send(STATUS_RICH_PUSH_UPDATE_SUCCESS, bundle);
            } else {
                receiver.send(STATUS_RICH_PUSH_UPDATE_ERROR, bundle);
            }
        }
    }

    /**
     * Deliver a result to the receiver.
     *
     * @param receiver The result receiver.
     * @param status A boolean indicating whether rich push update succeeded.
     */
    private void respond(ResultReceiver receiver, boolean status) {
        this.respond(receiver, status, null);
    }

    // Main entry-points

    /**
     * Update the inbox messages.
     *
     * @param receiver The result receiver.
     */
    private void messagesUpdate(ResultReceiver receiver) {
        boolean success = this.updateMessages();
        this.respond(receiver, success);

        // Do clean up
        this.handleReadMessages();
        this.handleDeletedMessages();
    }

    /**
     * Update the user.
     *
     * @param receiver The result receiver.
     */
    private void userUpdate(ResultReceiver receiver) {
        boolean success;
        if (!RichPushUser.isCreated()) {
            success = this.createUser();
        } else {
            success = this.updateUser();
        }

        this.respond(receiver, success);
    }

    //actions

    /**
     * Create the user.
     *
     * @return <code>true</code> if user was created, otherwise <code>false</code>.
     */
    private boolean createUser() {
        JSONObject payload;
        try {
            payload = createNewUserPayload();
        } catch (JSONException e) {
            Logger.error("Exception constructing JSON data when creating user.", e);
            return false;
        }

        Logger.info("Creating Rich Push user.");

        UserResponse response = userClient.createUser(payload);
        if (response == null) {
            return false;
        } else {
            if (getUser().setUser(response.getUserId(), response.getUserToken())) {
                Logger.info("Rich Push user created.");
                getUser().setLastUpdateTime(System.currentTimeMillis());
                return true;
            } else {
                Logger.warn("Rich Push user creation failed.");
                return false;
            }
        }
    }

    /**
     * Update the user.
     *
     * @return <code>true</code> if user was updated, otherwise <code>false</code>.
     */
    private boolean updateUser() {
        if (UAStringUtil.isEmpty(UAirship.shared().getPushManager().getChannelId())) {
            Logger.debug("RichPushUpdateService - No Channel. Skipping Rich Push user update.");
            return false;
        }

        Logger.info("Updating Rich Push user.");

        JSONObject payload;

        try {
            payload = createUpdateUserPayload();
        } catch (JSONException e) {
            Logger.error("Exception constructing JSON data when updating user.", e);
            return false;
        }

        if (userClient.updateUser(payload, getUser().getId(), getUser().getPassword())) {
            Logger.info("Rich Push user updated.");

            getUser().setLastUpdateTime(System.currentTimeMillis());
            return true;
        } else {
            getUser().setLastUpdateTime(0);
            return false;
        }
    }

    /**
     * Create the new user payload.
     *
     * @return The user payload as a JSON object.
     */
    private JSONObject createNewUserPayload() throws JSONException {
        JSONObject payload = new JSONObject();

        String channelId = UAirship.shared().getPushManager().getChannelId();
        if (!UAStringUtil.isEmpty(channelId)) {
            JSONArray array = new JSONArray();
            array.put(channelId);
            payload.putOpt(getPayloadChannelsKey(), array);
        }

        return payload;
    }

    /**
     * Create the user update payload.
     *
     * @return The user payload as a JSON object.
     */
    private JSONObject createUpdateUserPayload() throws JSONException {
        JSONObject payload = new JSONObject();
        JSONObject channelPayload = new JSONObject();

        JSONArray channels = new JSONArray();
        channels.put(UAirship.shared().getPushManager().getChannelId());

        channelPayload.put(PAYLOAD_ADD_KEY, channels);
        payload.put(getPayloadChannelsKey(), channelPayload);

        return payload;
    }

    /**
     * Get the payload channels key based on the platform.
     *
     * @return The payload channels key as a string.
     */
    private String getPayloadChannelsKey() {
        switch (UAirship.shared().getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                return PAYLOAD_AMAZON_CHANNELS_KEY;

            default:
                return PAYLOAD_ANDROID_CHANNELS_KEY;
        }
    }

    /**
     * Handle deletion of messages.
     */
    private void handleDeletedMessages() {
        Set<String> idsToDelete = getMessageIdsFromCursor(resolver.getDeletedMessages());

        if (idsToDelete != null && idsToDelete.size() > 0) {
            Logger.verbose("RichPushUpdateService - Found " + idsToDelete.size() + " messages to delete.");

            // Note: If we can't delete the messages on the server, leave them untouched
            // and we'll get them next time.
            if (this.deleteMessagesOnServer(idsToDelete)) {
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
            Logger.verbose("RichPushUpdateService - Found " + idsToUpdate.size() + " messages to mark read.");

            /*
            Note: If we can't mark the messages read on the server, leave them untouched
            and we'll get them next time.
             */
            if (this.markMessagesReadOnServer(idsToUpdate)) {
                ContentValues values = new ContentValues();
                values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, 0);
                resolver.updateMessages(idsToUpdate, values);
            }
        }
    }

    /**
     * Update the inbox messages.
     *
     * @return <code>true</code> if messages were updated, otherwise <code>false</code>.
     */
    private boolean updateMessages() {
        RichPushUser user = getUser();
        MessageListResponse response = userClient.getMessages(user.getId(),
                user.getPassword(),
                user.getLastMessageRefreshTime());

        Logger.info("Refreshing inbox messages.");

        if (response == null) {
            Logger.debug("RichPushUpdateService - Inbox message list request failed.");
            return false;
        }

        Logger.debug("RichPushUpdateService - Inbox message list request received: " + response.getStatus());

        switch (response.getStatus()) {
            case HttpStatus.SC_NOT_MODIFIED:
                Logger.info("Inbox messages already up-to-date. ");
                return true;

            case HttpStatus.SC_OK:
                ContentValues[] serverMessages = response.getServerMessages();
                if (serverMessages == null) {
                    Logger.info("Inbox message list is empty.");
                } else {
                    Logger.info("Received " + serverMessages.length + " inbox messages.");
                    updateInbox(serverMessages);
                    user.setLastMessageRefreshTime(response.getLastModifiedTimeMS());
                }
                return true;

            default:
                Logger.info("Unable to update inbox messages.");
                return false;
        }
    }


    // http actions

    /**
     * Delete the messages on the server.
     *
     * @param deletedIds A set of deletedId strings.
     * @return <code>true</code> if messages were deleted, otherwise <code>false</code>.
     */
    private boolean deleteMessagesOnServer(Set<String> deletedIds) {
        JSONObject payload = buildMessagesPayload(DELETE_MESSAGES_KEY, deletedIds);
        return userClient.deleteMessages(payload, getUser().getId(), getUser().getPassword());
    }

    /**
     * Mark the messages read on the server.
     *
     * @param readIds A set of readId strings.
     * @return <code>true</code> if messages marked read, otherwise <code>false</code>.
     */
    private boolean markMessagesReadOnServer(Set<String> readIds) {
        JSONObject payload = buildMessagesPayload(MARK_READ_MESSAGES_KEY, readIds);
        return userClient.markMessagesRead(payload, getUser().getId(), getUser().getPassword());
    }


    // helpers

    private JSONObject buildMessagesPayload(String root, Set<String> ids) {
        try {
            JSONObject payload = new JSONObject();
            payload.put(root, new JSONArray());
            String userId = this.getUser().getId();
            for (String id : ids) {
                payload.accumulate(root, this.formatUrl(MESSAGE_URL, new String[] { userId, id }));
            }
            Logger.verbose(payload.toString());
            return payload;
        } catch (JSONException e) {
            Logger.info(e.getMessage());
        }
        return null;
    }

    private String formatUrl(String urlFormat, String[] urlParams) {
        return this.getHostUrl() + String.format(urlFormat, (Object[]) urlParams);
    }


    /**
     * Get the rich push user.
     *
     * @return The rich push user.
     */
    private RichPushUser getUser() {
        return UAirship.shared().getRichPushManager().getRichPushUser();
    }

    /**
     * Get the host URL.
     *
     * @return The host URL.
     */
    private String getHostUrl() {
        return UAirship.shared().getAirshipConfigOptions().hostURL;
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
            UAirship.shared().getRichPushManager().getRichPushInbox().deleteMessages(allIds);
        }

        // update the inbox cache
        UAirship.shared().getRichPushManager().getRichPushInbox().updateCache();
    }

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

}
