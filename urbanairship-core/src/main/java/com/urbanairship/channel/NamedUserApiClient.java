/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * A high level abstraction for performing Named User API requests.
 */
class NamedUserApiClient {

    private static final String ASSOCIATE_PATH = "api/named_users/associate/";
    private static final String DISASSOCIATE_PATH = "api/named_users/disassociate/";

    private static final String CHANNEL_KEY = "channel_id";
    private static final String DEVICE_TYPE_KEY = "device_type";
    private static final String NAMED_USER_ID_KEY = "named_user_id";

    private final RequestFactory requestFactory;
    private final AirshipRuntimeConfig runtimeConfig;

    NamedUserApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    NamedUserApiClient(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
    }

    /**
     * Associates the channel to the named user ID.
     *
     * @param id The named user ID string.
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    @NonNull
    Response<Void> associate(@NonNull String id, @NonNull String channelId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(ASSOCIATE_PATH)
                               .build();

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_KEY, channelId)
                                 .put(DEVICE_TYPE_KEY, getDeviceType())
                                 .put(NAMED_USER_ID_KEY, id)
                                 .build();

        return requestFactory.createRequest()
                             .setOperation("POST", url)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(payload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute();
    }

    /**
     * Disassociate the channel from the named user ID.
     *
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    @NonNull
    Response<Void> disassociate(@NonNull String channelId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(DISASSOCIATE_PATH)
                               .build();

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_KEY, channelId)
                                 .put(DEVICE_TYPE_KEY, getDeviceType())
                                 .build();

        return requestFactory.createRequest()
                             .setOperation("POST", url)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(payload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute();
    }

    /**
     * Returns the device type based on the platform.
     *
     * @return The device type string.
     */
    @NonNull
    String getDeviceType() throws RequestException {
        switch (runtimeConfig.getPlatform()) {
            case UAirship.AMAZON_PLATFORM:
                return "amazon";

            case UAirship.ANDROID_PLATFORM:
                return "android";
            default:
                throw new RequestException("Invalid platform");
        }
    }
}
