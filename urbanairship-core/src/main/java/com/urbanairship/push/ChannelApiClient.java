/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import java.net.URL;

/**
 * A high level abstraction for performing Channel requests.
 */
class ChannelApiClient extends BaseApiClient {

    static final String CHANNEL_CREATION_PATH = "api/channels/";


    ChannelApiClient(@UAirship.Platform int platform, @NonNull AirshipConfigOptions configOptions) {
        this(platform, configOptions, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    ChannelApiClient(@UAirship.Platform int platform, @NonNull AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        super(platform, configOptions, requestFactory);
    }

    /**
     * Create the Channel ID
     *
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return response or null if an error occurred
     */
    @Nullable
    Response createChannelWithPayload(@NonNull ChannelRegistrationPayload channelPayload) {
        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Creating channel with payload: %s", payload);
        return performRequest(getDeviceUrl(CHANNEL_CREATION_PATH), "POST", payload);
    }

    /**
     * Update the Channel ID
     *
     * @param channelLocation The location of the channel as a URL
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return response or null if an error occurred
     */
    @Nullable
    Response updateChannelWithPayload(@NonNull URL channelLocation, @NonNull ChannelRegistrationPayload channelPayload) {
        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Updating channel with payload: %s", payload);
        return performRequest(channelLocation, "PUT", payload);
    }
}
