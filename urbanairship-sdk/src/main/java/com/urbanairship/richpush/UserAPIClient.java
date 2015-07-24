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
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.RichPushTable;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A high level abstraction for performing User API creation and updates.
 */
class UserAPIClient {

    private static final String USER_CREATION_PATH = "api/user/";
    private static final String USER_UPDATE_PATH = "api/user/%s/";
    private static final String DELETE_MESSAGES_PATH = "api/user/%s/messages/delete/";
    private static final String MARK_READ_MESSAGES_PATH = "api/user/%s/messages/unread/";
    private static final String MESSAGES_PATH = "api/user/%s/messages/";

    private final RequestFactory requestFactory;

    UserAPIClient() {
        this(new RequestFactory());
    }

    /**
     * Create the UserAPIClient.
     *
     * @param requestFactory The requestFactory.
     */
    UserAPIClient(RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
    }

    /**
     * Create the Rich Push User.
     *
     * @param userPayload The user payload.
     * @return userResponse or null if an error occurred.
     */
    UserResponse createUser(@NonNull JSONObject userPayload) {
        String appKey = UAirship.shared().getAirshipConfigOptions().getAppKey();
        String appSecret = UAirship.shared().getAirshipConfigOptions().getAppSecret();

        URL userCreationURL = getUserURL(USER_CREATION_PATH);
        if (userCreationURL == null) {
            return null;
        }

        Logger.verbose("UserAPIClient - Creating Rich Push user with payload: " + userPayload);
        Response response = requestFactory.createRequest("POST", userCreationURL)
                                          .setCredentials(appKey, appSecret)
                                          .setRequestBody(userPayload.toString(), "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        if (response == null) {
            Logger.verbose("UserAPIClient - Failed to receive response for Rich Push user creation.");
            return null;
        } else if (response.getStatus() == HttpURLConnection.HTTP_CREATED) {
            String userId;
            String userToken;
            try {
                userId = new JSONObject(response.getResponseBody()).getString("user_id");
                userToken = new JSONObject(response.getResponseBody()).getString("password");
            } catch (JSONException e) {
                Logger.error("UserAPIClient - Unable to parse Rich Push user response: " + response);
                return null;
            }
            return new UserResponse(userId, userToken);
        } else {
            Logger.verbose("UserAPIClient - Rich Push user creation failed: " + response);
            return null;
        }
    }

    /**
     * Update the Rich Push User.
     *
     * @param userPayload The user payload.
     * @param userId The user ID.
     * @param userToken The user token.
     * @return <code>true</code> if user was updated, otherwise <code>false</code>.
     */
    boolean updateUser(JSONObject userPayload, String userId, String userToken) {
        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
            Logger.error("Unable to update user with a null userId or null userToken.");
            return false;
        }

        URL userUpdateURL = getUserURL(USER_UPDATE_PATH, userId);
        if (userUpdateURL == null) {
            return false;
        }

        Logger.verbose("UserAPIClient - Updating user with payload: " + userPayload);
        Response response = requestFactory.createRequest("POST", userUpdateURL)
                                          .setCredentials(userId, userToken)
                                          .setRequestBody(userPayload.toString(), "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("UserAPIClient - Update Rich Push user response: " + response);
        return response != null && response.getStatus() == HttpURLConnection.HTTP_OK;
    }

    /**
     * Performs a delete messages request.
     *
     * @param messagePayload The payload that contains the messages to be deleted.
     * @param userId The user ID.
     * @param userToken The user token.
     * @return <code>true</code> if messages were deleted, otherwise <code>false</code>.
     */
    boolean deleteMessages(JSONObject messagePayload, String userId, String userToken) {
        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
            Logger.error("UserAPIClient - Unable to delete messages with a null userId or null userToken.");
            return false;
        }

        URL deleteMessagesURL = getUserURL(DELETE_MESSAGES_PATH, userId);
        if (deleteMessagesURL == null) {
            return false;
        }

        Logger.verbose("UserAPIClient - Deleting inbox messages with payload: " + messagePayload);
        Response response = requestFactory.createRequest("POST", deleteMessagesURL)
                                          .setCredentials(userId, userToken)
                                          .setRequestBody(messagePayload.toString(), "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("UserAPIClient - Delete inbox messages response: " + response);
        return response != null && response.getStatus() == HttpURLConnection.HTTP_OK;
    }

    /**
     * Mark messages unread on the server.
     *
     * @param messagePayload The payload that contains the messages to be marked unread.
     * @param userId The user ID.
     * @param userToken The user token.
     * @return <code>true</code> if messages marked read, otherwise <code>false</code>.
     */
    boolean markMessagesRead(JSONObject messagePayload, String userId, String userToken) {
        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
            Logger.error("Unable to mark messages read with a null userId or null userToken.");
            return false;
        }

        URL markMessagesReadURL = getUserURL(MARK_READ_MESSAGES_PATH, userId);
        if (markMessagesReadURL == null) {
            return false;
        }

        Logger.verbose("UserAPIClient - Marking inbox messages read request with payload: " + messagePayload);
        Response response = requestFactory.createRequest("POST", markMessagesReadURL)
                                          .setCredentials(userId, userToken)
                                          .setRequestBody(messagePayload.toString(), "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("UserAPIClient - Mark inbox messages read response: " + response);
        return response != null && response.getStatus() == HttpURLConnection.HTTP_OK;
    }

    /**
     * Get messages from the server.
     *
     * @param userId The user ID.
     * @param userToken The user token.
     * @param ifModifiedSinceMS The last message refresh time in milliseconds.
     * @return A message list response, or null if the server was unable to be reached.
     */
    MessageListResponse getMessages(String userId, String userToken, long ifModifiedSinceMS) {
        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
            Logger.error("Unable to get messages with a null userId or null userToken.");
            return null;
        }

        URL getMessagesURL = getUserURL(MESSAGES_PATH, userId);
        if (getMessagesURL == null) {
            return null;
        }

        Logger.verbose("UserAPIClient - Fetching inbox messages.");
        Response response = requestFactory.createRequest("GET", getMessagesURL)
                                          .setCredentials(userId, userToken)
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .setIfModifiedSince(ifModifiedSinceMS)
                                          .execute();

        Logger.verbose("UserAPIClient - Fetch inbox messages response: " + response);

        if (response == null) {
            return null;
        } else {

            ContentValues[] messages = null;
            if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
                try {
                    messages = messagesFromResponse(response.getResponseBody());
                } catch (JSONException e) {
                    Logger.error("Unable to parse messages.", e);
                    return null;
                }
            }

            return new MessageListResponse(messages, response.getStatus(), response.getLastModifiedTime());
        }

    }

    /**
     * Get the URL.
     *
     * @param path The url path.
     * @param args Url arguments.
     * @return The URL or null if an error occurred.
     */
    private URL getUserURL(String path, Object... args) {
        String hostURL = UAirship.shared().getAirshipConfigOptions().hostURL;
        String urlString = String.format(hostURL + path, args);
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid userURL", e);
        }
        return null;
    }

    private ContentValues[] messagesFromResponse(String messagesString) throws JSONException {
        if (messagesString == null) {
            return null;
        }

        JSONArray messagesJsonArray = new JSONObject(messagesString).getJSONArray("messages");

        int count = messagesJsonArray.length();
        ContentValues[] messages = new ContentValues[count];

        for (int i = 0; i < count; i++) {
            JSONObject messageJson = messagesJsonArray.getJSONObject(i);

            ContentValues values = new ContentValues();
            values.put(RichPushTable.COLUMN_NAME_TIMESTAMP, messageJson.getString("message_sent"));
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_ID, messageJson.getString("message_id"));
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_URL, messageJson.getString("message_url"));
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL, messageJson.getString("message_body_url"));
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_READ_URL, messageJson.getString("message_read_url"));
            values.put(RichPushTable.COLUMN_NAME_TITLE, messageJson.getString("title"));
            values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, messageJson.getBoolean("unread"));

            values.put(RichPushTable.COLUMN_NAME_EXTRA, messageJson.getJSONObject("extra").toString());
            values.put(RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT, messageJson.toString());

            if (messageJson.has("message_expiry")) {
                values.put(RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP, messageJson.getString("message_expiry"));
            }

            messages[i] = values;
        }

        return messages;
    }
}
