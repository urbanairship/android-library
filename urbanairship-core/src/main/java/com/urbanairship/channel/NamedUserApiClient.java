/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * A high level abstraction for performing Named User API requests.
 */
class NamedUserApiClient extends BaseApiClient {

    private static final String ASSOCIATE_PATH = "api/named_users/associate/";
    private static final String DISASSOCIATE_PATH = "api/named_users/disassociate/";

    private static final String CHANNEL_KEY = "channel_id";
    private static final String DEVICE_TYPE_KEY = "device_type";
    private static final String NAMED_USER_ID_KEY = "named_user_id";

    private AirshipRuntimeConfig runtimeConfig;

    NamedUserApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    NamedUserApiClient(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull RequestFactory requestFactory) {
        super(runtimeConfig, requestFactory);
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Associates the channel to the named user ID.
     *
     * @param id The named user ID string.
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    @Nullable
    Response associate(@NonNull String id, @NonNull String channelId) {
        URL associateUrl = runtimeConfig.getUrlConfig()
                                            .deviceUrl()
                                            .appendEncodedPath(ASSOCIATE_PATH)
                                            .build();

        if (associateUrl == null) {
            Logger.debug("Named User URL null. Unable to associate named user.");
            return null;
        }

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_KEY, channelId)
                                 .put(DEVICE_TYPE_KEY, getDeviceType())
                                 .put(NAMED_USER_ID_KEY, id)
                                 .build();

        return performRequest(associateUrl, "POST", payload.toString());
    }

    /**
     * Disassociate the channel from the named user ID.
     *
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    @Nullable
    Response disassociate(@NonNull String channelId) {
        URL disassociateUrl = runtimeConfig.getUrlConfig()
                                               .deviceUrl()
                                               .appendEncodedPath(DISASSOCIATE_PATH)
                                               .build();

        if (disassociateUrl == null) {
            Logger.debug("Named User URL null. Unable to disassociate named user.");
            return null;
        }

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_KEY, channelId)
                                 .put(DEVICE_TYPE_KEY, getDeviceType())
                                 .build();

        return performRequest(disassociateUrl, "POST", payload.toString());
    }

    /**
     * Returns the device type based on the platform.
     *
     * @return The device type string.
     */
    @NonNull
    String getDeviceType() {
        switch (runtimeConfig.getPlatform()) {
            case UAirship.AMAZON_PLATFORM:
                return "amazon";

            case UAirship.ANDROID_PLATFORM:
            default:
                return "android";
        }
    }

}
