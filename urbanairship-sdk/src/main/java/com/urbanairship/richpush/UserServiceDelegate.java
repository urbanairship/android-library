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
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Service delegate for the {@link RichPushUpdateService} to handle user registrations.
 */
class UserServiceDelegate extends BaseIntentService.Delegate {

    private static final String LAST_UPDATE_TIME ="com.urbanairship.user.LAST_UPDATE_TIME";
    static final long USER_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    private static final String PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels";
    private static final String PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels";
    private static final String PAYLOAD_ADD_KEY = "add";

    private final UserAPIClient userClient;
    private final UAirship airship;
    private final RichPushUser user;

    UserServiceDelegate(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        this(context, dataStore, new UserAPIClient(), UAirship.shared());
    }

    UserServiceDelegate(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
                        @NonNull UserAPIClient userClient, @NonNull UAirship airship) {
        super(context, dataStore);

        this.userClient = userClient;
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
            Logger.debug("RichPushServiceDelegate - No Channel. User will be created after channel registrations finishes.");
            return false;
        }

        JSONObject payload;
        try {
            payload = createNewUserPayload(channelId);
        } catch (JSONException e) {
            Logger.error("Exception constructing JSON data when creating user.", e);
            return false;
        }

        Logger.info("Creating Rich Push user.");

        UserResponse response = userClient.createUser(payload);
        if (response == null) {
            return false;
        } else {
            if (user.setUser(response.getUserId(), response.getUserToken())) {
                Logger.info("Rich Push user created.");
                getDataStore().put(LAST_UPDATE_TIME, System.currentTimeMillis());
                getDataStore().remove(RichPushUpdateService.LAST_MESSAGE_REFRESH_TIME);
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
        String channelId = airship.getPushManager().getChannelId();

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.debug("RichPushServiceDelegate - No Channel. Skipping Rich Push user update.");
            return false;
        }

        Logger.info("Updating Rich Push user.");

        JSONObject payload;

        try {
            payload = createUpdateUserPayload(channelId);
        } catch (JSONException e) {
            Logger.error("Exception constructing JSON data when updating user.", e);
            return false;
        }

        if (userClient.updateUser(payload, user.getId(), user.getPassword())) {
            Logger.info("Rich Push user updated.");

            getDataStore().put(LAST_UPDATE_TIME, System.currentTimeMillis());
            return true;
        } else {
            getDataStore().put(LAST_UPDATE_TIME, 0);
            return false;
        }
    }

    /**
     * Create the new user payload.
     *
     * @return The user payload as a JSON object.
     */
    private JSONObject createNewUserPayload(@NonNull String channelId) throws JSONException {
        JSONObject payload = new JSONObject();
        JSONArray array = new JSONArray();
        array.put(channelId);
        payload.putOpt(getPayloadChannelsKey(), array);

        return payload;
    }

    /**
     * Create the user update payload.
     *
     * @return The user payload as a JSON object.
     */
    private JSONObject createUpdateUserPayload(@NonNull String channelId) throws JSONException {
        JSONObject payload = new JSONObject();
        JSONObject channelPayload = new JSONObject();

        JSONArray channels = new JSONArray();
        channels.put(channelId);

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
        switch (airship.getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                return PAYLOAD_AMAZON_CHANNELS_KEY;

            default:
                return PAYLOAD_ANDROID_CHANNELS_KEY;
        }
    }
}
