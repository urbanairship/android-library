/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import java.net.URL;

/**
 * A high level abstraction for performing Named User API requests.
 */
class NamedUserApiClient extends BaseApiClient {

    private static final String TAG_GROUP_PATH = "api/named_users/tags/";
    private static final String ASSOCIATE_PATH = "api/named_users/associate/";
    private static final String DISASSOCIATE_PATH = "api/named_users/disassociate/";

    static final String CHANNEL_KEY = "channel_id";
    static final String DEVICE_TYPE_KEY = "device_type";
    static final String NAMED_USER_ID_KEY = "named_user_id";
    private final int platform;

    NamedUserApiClient(@UAirship.Platform int platform, AirshipConfigOptions configOptions) {
        this(platform, configOptions, new RequestFactory());
    }

    @VisibleForTesting
    NamedUserApiClient(@UAirship.Platform int platform, AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        super(configOptions, requestFactory);
        this.platform = platform;
    }

    /**
     * Associates the channel to the named user ID.
     *
     * @param id The named user ID string.
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    Response associate(@NonNull String id, @NonNull String channelId) {
        JsonMap payload = JsonMap.newBuilder()
                .put(CHANNEL_KEY, channelId)
                .put(DEVICE_TYPE_KEY, getDeviceType())
                .put(NAMED_USER_ID_KEY, id)
                .build();

        URL associateUrl = getDeviceUrl(ASSOCIATE_PATH);
        return performRequest(associateUrl, "POST", payload.toString());
    }

    /**
     * Disassociate the channel from the named user ID.
     *
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    Response disassociate(@NonNull String channelId) {
        JsonMap payload = JsonMap.newBuilder()
                .put(CHANNEL_KEY, channelId)
                .put(DEVICE_TYPE_KEY, getDeviceType())
                .build();

        URL disassociateUrl = getDeviceUrl(DISASSOCIATE_PATH);
        return performRequest(disassociateUrl, "POST", payload.toString());
    }

    /**
     * Returns the device type based on the platform.
     *
     * @return The device type string.
     */
    String getDeviceType() {
        switch (platform) {
            case UAirship.AMAZON_PLATFORM:
                return "amazon";

            case UAirship.ANDROID_PLATFORM:
            default:
                return  "android";
        }
    }

    @Override
    protected String getTagGroupAudienceSelector() {
        return NAMED_USER_ID_KEY;
    }

    @Override
    protected String getTagGroupPath() {
        return TAG_GROUP_PATH;
    }
}
