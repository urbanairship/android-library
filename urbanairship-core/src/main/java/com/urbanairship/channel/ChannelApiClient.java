/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.net.URL;

import androidx.annotation.NonNull;
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

    /**
     * Default constructor.
     *
     * @param configOptions Airship config options.
     */
    ChannelApiClient(@NonNull AirshipConfigOptions configOptions) {
        this(configOptions, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    ChannelApiClient(@NonNull AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        super(configOptions, requestFactory);
    }

    /**
     * Create the Channel ID
     *
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return The channel ID.
     */
    @NonNull
    ChannelResponse<String> createChannelWithPayload(@NonNull ChannelRegistrationPayload channelPayload) throws ChannelRequestException {
        URL url = getDeviceUrl(CHANNEL_API_PATH);
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
        URL url = getDeviceUrl(CHANNEL_API_PATH + channelId);
        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Updating channel with payload: %s", payload);

        Response response = performRequest(url, "PUT", payload);
        if (response == null) {
            throw new ChannelRequestException("Failed to get a response");
        }

        return new ChannelResponse<>(null, response);
    }

}
