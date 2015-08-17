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
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Service delegate for the {@link RichPushUpdateService} to handle user registrations.
 */
class UserServiceDelegate extends BaseIntentService.Delegate {

    private static final String USER_CREATION_PATH = "api/user/";
    private static final String USER_UPDATE_PATH = "api/user/%s/";

    private static final String LAST_UPDATE_TIME ="com.urbanairship.user.LAST_UPDATE_TIME";
    static final long USER_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    private static final String PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels";
    private static final String PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels";
    private static final String PAYLOAD_ADD_KEY = "add";

    private final UAirship airship;
    private final RichPushUser user;
    private final RequestFactory requestFactory;

    UserServiceDelegate(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        this(context, dataStore, new RequestFactory(), UAirship.shared());
    }

    UserServiceDelegate(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
                        @NonNull RequestFactory requestFactory, @NonNull UAirship airship) {
        super(context, dataStore);

        this.requestFactory = requestFactory;
        this.airship = airship;
        this.user = airship.getRichPushManager().getRichPushUser();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE.equals(intent.getAction())) {
            return;
        }

        if (intent.getBooleanExtra(RichPushUpdateService.EXTRA_FORCEFULLY, false)) {
            long lastUpdateTime = getDataStore().getLong(LAST_UPDATE_TIME, 0);
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

        RichPushUpdateService.respond(intent, success);
    }

    /**
     * Create the user.
     *
     * @return <code>true</code> if user was created, otherwise <code>false</code>.
     */
    private boolean createUser() {
        String channelId = airship.getPushManager().getChannelId();
        if (UAStringUtil.isEmpty(channelId)) {
            Logger.debug("UserServiceDelegate - No Channel. User will be created after channel registrations finishes.");
            return false;
        }

        URL userCreationURL = RichPushUpdateService.getUserURL(USER_CREATION_PATH);
        if (userCreationURL == null) {
            return false;
        }

        String payload = createNewUserPayload(channelId);
        Logger.verbose("UserServiceDelegate - Creating Rich Push user with payload: " + payload);
        Response response = requestFactory.createRequest("POST", userCreationURL)
                                          .setCredentials(airship.getAirshipConfigOptions().getAppKey(), airship.getAirshipConfigOptions().getAppSecret())
                                          .setRequestBody(payload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        // Check for failure
        if (response == null || response.getStatus() != HttpURLConnection.HTTP_CREATED) {
            Logger.verbose("UserServiceDelegate - Rich Push user creation failed: " + response);
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
            Logger.error("UserServiceDelegate - Unable to parse Rich Push user response: " + response);
            return false;
        }

        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
            Logger.error("UserServiceDelegate - Rich Push user creation failed: " + response);
            return false;
        }

        Logger.info("Created Rich Push user: " + userId);
        getDataStore().put(LAST_UPDATE_TIME, System.currentTimeMillis());
        getDataStore().remove(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME);
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
            Logger.debug("UserServiceDelegate - No Channel. Skipping Rich Push user update.");
            return false;
        }


        URL userUpdateURL = RichPushUpdateService.getUserURL(USER_UPDATE_PATH, user.getId());
        if (userUpdateURL == null) {
            return false;
        }

        String payload = createUpdateUserPayload(channelId);
        Logger.verbose("UserServiceDelegate - Updating user with payload: " + payload);
        Response response = requestFactory.createRequest("POST", userUpdateURL)
                                          .setCredentials(user.getId(), user.getPassword())
                                          .setRequestBody(payload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        Logger.verbose("UserServiceDelegate - Update Rich Push user response: " + response);
        if (response != null && response.getStatus() == HttpURLConnection.HTTP_OK) {
            Logger.info("Rich Push user updated.");
            getDataStore().put(LAST_UPDATE_TIME, System.currentTimeMillis());
            return true;
        }

        getDataStore().put(LAST_UPDATE_TIME, 0);
        return false;
    }

    /**
     * Create the new user payload.
     *
     * @return The user payload as a JSON object.
     */
    private String createNewUserPayload(@NonNull String channelId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(getPayloadChannelsKey(), Arrays.asList(channelId));
        return JsonValue.wrap(payload, JsonValue.NULL).toString();
    }

    /**
     * Create the user update payload.
     *
     * @return The user payload as a JSON object.
     */
    private String createUpdateUserPayload(@NonNull String channelId) {
        Map<String, Object> addChannels = new HashMap<>();
        addChannels.put(getPayloadChannelsKey(), Arrays.asList(channelId));

        Map<String, Object> payload = new HashMap<>();
        payload.put(PAYLOAD_ADD_KEY, addChannels);

        return JsonValue.wrap(payload, JsonValue.NULL).toString();
    }

    /**
     * Get the payload channels key based on the platform.
     *
     * @return The payload channels key as a string.
     */
    private String getPayloadChannelsKey() {
        switch (airship.getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                return PAYLOAD_AMAZON_CHANNELS_KEY;

            default:
                return PAYLOAD_ANDROID_CHANNELS_KEY;
        }
    }
}
