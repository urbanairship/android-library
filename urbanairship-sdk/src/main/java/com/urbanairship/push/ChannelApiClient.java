/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
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
    private static final String CHANNEL_TAGS_PATH = "api/channels/tags/";

    private static final String ANDROID_CHANNEL_KEY = "android_channel";
    private static final String AMAZON_CHANNEL_KEY = "amazon_channel";
    private final int platform;

    ChannelApiClient(@UAirship.Platform int platform, AirshipConfigOptions configOptions) {
        this(platform, configOptions, new RequestFactory());
    }

    @VisibleForTesting
    ChannelApiClient(@UAirship.Platform int platform, AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        super(configOptions, requestFactory);
        this.platform = platform;
    }

    /**
     * Create the Channel ID
     *
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return response or null if an error occurred
     */
    Response createChannelWithPayload(@NonNull ChannelRegistrationPayload channelPayload) {
        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Creating channel with payload: " + payload);
        return performRequest(getDeviceUrl(CHANNEL_CREATION_PATH), "POST", payload);
    }

    /**
     * Update the Channel ID
     *
     * @param channelLocation The location of the channel as a URL
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return response or null if an error occurred
     */
    Response updateChannelWithPayload(@NonNull URL channelLocation, @NonNull ChannelRegistrationPayload channelPayload) {
        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Updating channel with payload: " + payload);
        return performRequest(channelLocation, "PUT", payload);
    }

    @Override
    protected String getTagGroupAudienceSelector() {
        switch (platform) {
            case UAirship.AMAZON_PLATFORM:
                return AMAZON_CHANNEL_KEY;

            case UAirship.ANDROID_PLATFORM:
            default:
                return ANDROID_CHANNEL_KEY;
        }
    }

    @Override
    protected String getTagGroupPath() {
        return CHANNEL_TAGS_PATH;
    }
}
