/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.UrlBuilder;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * A high level abstraction for performing Channel requests.
 */
class ChannelApiClient {

    private static final String CHANNEL_API_PATH = "api/channels/";

    /**
     * Response body key for the channel ID.
     */
    private static final String CHANNEL_ID_KEY = "channel_id";

    private final RequestFactory requestFactory;
    private final AirshipRuntimeConfig runtimeConfig;

    /**
     * Default constructor.
     *
     * @param runtimeConfig Airship runtime config.
     */
    ChannelApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    ChannelApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                     @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
    }

    /**
     * Create the Channel ID
     *
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return The channel ID.
     */
    @NonNull
    Response<String> createChannelWithPayload(@NonNull ChannelRegistrationPayload channelPayload) throws RequestException {
        Logger.verbose("Creating channel with payload: %s", channelPayload);
        return requestFactory.createRequest()
                             .setOperation("POST", getDeviceUrl(null))
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(channelPayload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute(new ResponseParser<String>() {
                                 @Override
                                 public String parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                                     if (UAHttpStatusUtil.inSuccessRange(status)) {
                                         return JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID_KEY).getString();
                                     }
                                     return null;
                                 }
                             });
    }

    /**
     * Update the Channel ID
     *
     * @param channelId The channel identifier
     * @param channelPayload An instance of ChannelRegistrationPayload
     */
    @NonNull
    Response<Void> updateChannelWithPayload(@NonNull String channelId, @NonNull ChannelRegistrationPayload channelPayload) throws RequestException {
        Logger.verbose("Updating channel with payload: %s", channelPayload);

        return requestFactory.createRequest()
                             .setOperation("PUT", getDeviceUrl(channelId))
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(channelPayload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute();
    }

    @Nullable
    private Uri getDeviceUrl(@Nullable String channelId) {
        UrlBuilder builder = runtimeConfig.getUrlConfig()
                                          .deviceUrl()
                                          .appendEncodedPath(CHANNEL_API_PATH);

        if (channelId != null) {
            builder.appendPath(channelId);
        }

        return builder.build();
    }

}
