/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A high level abstraction for performing Named User API association and disassociation.
 */
class NamedUserAPIClient {

    static final String NAMED_USER_PATH = "api/named_users";
    static final String CHANNEL_KEY = "channel_id";
    static final String DEVICE_TYPE_KEY = "device_type";
    static final String NAMED_USER_ID_KEY = "named_user_id";

    protected String urlString;
    private RequestFactory requestFactory;

    NamedUserAPIClient() {
        this(new RequestFactory());
    }

    NamedUserAPIClient(RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.urlString = UAirship.shared().getAirshipConfigOptions().hostURL + NAMED_USER_PATH;
    }

    /**
     * Associates the channel to the named user ID.
     *
     * @param id The named user ID string.
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    Response associate(@NonNull String id, @NonNull String channelId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put(CHANNEL_KEY, channelId);
            payload.put(DEVICE_TYPE_KEY, getDeviceType());
            payload.put(NAMED_USER_ID_KEY, id);

        } catch (Exception ex) {
            Logger.error("Failed to create associate named user payload as json.", ex);
        }

        URL associateUrl;
        String urlString = this.urlString + "/associate";
        try {
            associateUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid hostURL", e);
            return null;
        }

        return request(associateUrl, payload.toString());
    }

    /**
     * Disassociate the channel from the named user ID.
     *
     * @param channelId The channel ID string.
     * @return The response or null if an error occurred.
     */
    Response disassociate(@NonNull String channelId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put(CHANNEL_KEY, channelId);
            payload.put(DEVICE_TYPE_KEY, getDeviceType());

        } catch (Exception ex) {
            Logger.error("Failed to create disassociate named user payload as json.", ex);
        }

        URL disassociateUrl;
        String urlString = this.urlString + "/disassociate";
        try {
            disassociateUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid hostURL", e);
            return null;
        }

        return request(disassociateUrl, payload.toString());
    }

    /**
     * Sends the named user request.
     *
     * @param url The specified URL to send the request to.
     * @param jsonPayload The JSON payload as a string.
     * @return The response or null if an error occurred.
     */
    private Response request(@NonNull URL url, @NonNull String jsonPayload) {
        String appKey = UAirship.shared().getAirshipConfigOptions().getAppKey();
        String appSecret = UAirship.shared().getAirshipConfigOptions().getAppSecret();

        Response response = requestFactory.createRequest("POST", url)
                                          .setCredentials(appKey, appSecret)
                                          .setRequestBody(jsonPayload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        if (response == null) {
            Logger.error("Failed to receive a response for named user.");
        } else {
            Logger.debug("Received a response for named user: " + response);
        }

        return response;
    }

    /**
     * Returns the device type based on the platform.
     *
     * @return The device type string.
     */
    String getDeviceType() {
        String deviceType = null;
        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                deviceType = "android";
                break;
            case UAirship.AMAZON_PLATFORM:
                deviceType = "amazon";
                break;
        }
        return deviceType;
    }
}
