/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A high level abstraction for performing Channel API creation and updates.
 */
class ChannelApiClient {

    static final String CHANNEL_CREATION_PATH = "api/channels/";

    protected URL creationURL;
    private final RequestFactory requestFactory;

    ChannelApiClient() {
        this(new RequestFactory());
    }

    @VisibleForTesting
    ChannelApiClient(@NonNull RequestFactory requestFactory) {
        this.requestFactory = requestFactory;

        String urlString = UAirship.shared().getAirshipConfigOptions().hostURL + CHANNEL_CREATION_PATH;
        try {
            this.creationURL = new URL(urlString);
        } catch (MalformedURLException e) {
            this.creationURL = null;
            Logger.error("ChannelApiClient - Invalid hostURL    ", e);
        }
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
        return requestWithPayload(creationURL, "POST", payload);
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
        return requestWithPayload(channelLocation, "PUT", payload);
    }

    /**
     * Sends the channel creation or update request
     *
     * @param url The specified URL to send the request to.
     * @param requestMethod String representing the request method to use.
     * @param jsonPayload JSON payload as a string
     * @return response or null if an error occurred
     */
    private Response requestWithPayload(@NonNull URL url, @NonNull String requestMethod, @NonNull String jsonPayload) {
        String appKey = UAirship.shared().getAirshipConfigOptions().getAppKey();
        String appSecret = UAirship.shared().getAirshipConfigOptions().getAppSecret();

        Response response = requestFactory.createRequest(requestMethod, url)
                                          .setCredentials(appKey, appSecret)
                                          .setRequestBody(jsonPayload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        if (response == null) {
            Logger.debug("ChannelApiClient - Failed to receive channel response.");
            return null;
        }

        Logger.verbose("ChannelApiClient - Received channel response: " + response);
        return response;
    }
}
