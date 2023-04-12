/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.UrlBuilder;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;

import java.util.HashMap;
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

    private final RequestSession session;
    private final AirshipRuntimeConfig runtimeConfig;

    /**
     * Default constructor.
     *
     * @param runtimeConfig Airship runtime config.
     */
    ChannelApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, runtimeConfig.getRequestSession());
    }

    @VisibleForTesting
    ChannelApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                     @NonNull RequestSession session) {
        this.runtimeConfig = runtimeConfig;
        this.session = session;
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

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");

        Request request = new Request(
                getDeviceUrl(null),
                "POST",
                RequestAuth.BasicAppAuth.INSTANCE,
                new RequestBody.Json(channelPayload),
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> {
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                return JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID_KEY).getString();
            }
            return null;
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

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");

        Request request = new Request(
                getDeviceUrl(channelId),
                "PUT",
                RequestAuth.BasicAppAuth.INSTANCE,
                new RequestBody.Json(channelPayload),
                headers
        );

        return session.execute(request, (status, headers1, responseBody) -> null);
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

    boolean isUrlConfigured() {
        return runtimeConfig.getUrlConfig().isDeviceUrlAvailable();
    }
}
