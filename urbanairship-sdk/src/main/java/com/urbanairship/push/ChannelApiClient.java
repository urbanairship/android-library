/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
     * @return channelResponse or null if an error occurred
     */
    ChannelResponse createChannelWithPayload(@NonNull ChannelRegistrationPayload channelPayload) {
        String payload = channelPayload.toJsonValue().toString();
        Logger.verbose("ChannelApiClient - Creating channel with payload: " + payload);
        return requestWithPayload(creationURL, "POST", payload);
    }

    /**
     * Update the Channel ID
     *
     * @param channelLocation The location of the channel as a URL
     * @param channelPayload An instance of ChannelRegistrationPayload
     * @return channelResponse or null if an error occurred
     */
    ChannelResponse updateChannelWithPayload(@NonNull URL channelLocation, @NonNull ChannelRegistrationPayload channelPayload) {
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
     * @return channelResponse or null if an error occurred
     */
    private ChannelResponse requestWithPayload(@NonNull URL url, @NonNull String requestMethod, @NonNull String jsonPayload) {
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
        return new ChannelResponse(response);
    }
}
