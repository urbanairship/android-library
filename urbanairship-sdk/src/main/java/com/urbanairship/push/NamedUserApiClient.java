/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A high level abstraction for performing Named User API association and disassociation.
 */
class NamedUserApiClient {

    static final String NAMED_USER_PATH = "api/named_users";
    static final String CHANNEL_KEY = "channel_id";
    static final String DEVICE_TYPE_KEY = "device_type";
    static final String NAMED_USER_ID_KEY = "named_user_id";

    protected final String urlString;
    private final RequestFactory requestFactory;

    NamedUserApiClient() {
        this(new RequestFactory());
    }

    @VisibleForTesting
    NamedUserApiClient(@NonNull RequestFactory requestFactory) {
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
        JsonMap payload = JsonMap.newBuilder()
                .put(CHANNEL_KEY, channelId)
                .put(DEVICE_TYPE_KEY, getDeviceType())
                .put(NAMED_USER_ID_KEY, id)
                .build();

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
        JsonMap payload = JsonMap.newBuilder()
                .put(CHANNEL_KEY, channelId)
                .put(DEVICE_TYPE_KEY, getDeviceType())
                .build();

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
