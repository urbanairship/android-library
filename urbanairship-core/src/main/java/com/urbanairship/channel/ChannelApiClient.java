/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.UrlBuilder;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * A high level abstraction for performing Channel requests.
 */
class ChannelApiClient extends BaseApiClient {

    private static final String CHANNEL_API_PATH = "api/channels/";

    /**
     * Response body key for the channel ID.
     */
    private static final String CHANNEL_ID_KEY = "channel_id";

    private AirshipRuntimeConfig runtimeConfig;

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
        super(runtimeConfig, requestFactory);
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Create the Channel ID
     *
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return The channel ID.
     */
    @NonNull
    ChannelResponse<String> createChannelWithPayload(@NonNull ChannelRegistrationPayload channelPayload) throws ChannelRequestException {
        URL url = getDeviceUrl(null);
        if (url == null) {
            Logger.debug("CRA URL is null, unable to create channel.");
            throw new ChannelRequestException("Missing URL");
        }

        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Creating channel with payload: %s", payload);

        Response response = performRequest(url, "POST", payload);
        if (response == null) {
            throw new ChannelRequestException("Failed to get a response");
        }

        if (response.isSuccessful()) {
            String channelId;
            try {
                channelId = JsonValue.parseString(response.getResponseBody()).optMap().opt(CHANNEL_ID_KEY).getString();
            } catch (JsonException e) {
                throw new ChannelRequestException("Failed to parse response", e);
            }
            return new ChannelResponse<>(channelId, response);
        }

        return new ChannelResponse<>(null, response);
    }

    /**
     * Update the Channel ID
     *
     * @param channelId The channel identifier
     * @param channelPayload An instance of ChannelRegistrationPayload
     */
    ChannelResponse<Void> updateChannelWithPayload(@NonNull String channelId, @NonNull ChannelRegistrationPayload channelPayload) throws ChannelRequestException {
        URL url = getDeviceUrl(channelId);
        if (url == null) {
            Logger.debug("CRA URL is null, unable to update channel.");
            return null;
        }

        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Updating channel with payload: %s", payload);

        Response response = performRequest(url, "PUT", payload);
        if (response == null) {
            throw new ChannelRequestException("Failed to get a response");
        }

        return new ChannelResponse<>(null, response);
    }

    @Nullable
    private URL getDeviceUrl(@Nullable String channelId) {
        UrlBuilder builder = runtimeConfig.getUrlConfig()
                                          .deviceUrl()
                                          .appendEncodedPath(CHANNEL_API_PATH);

        if (channelId != null) {
            builder.appendPath(channelId);
        }

        return builder.build();
    }

}
