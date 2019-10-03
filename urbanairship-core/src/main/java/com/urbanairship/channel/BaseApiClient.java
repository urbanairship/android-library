/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Base class for the NamedUser and Channel Api Clients.
 */
abstract class BaseApiClient {

    private final AirshipConfigOptions configOptions;
    private final RequestFactory requestFactory;

    BaseApiClient(@NonNull AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.configOptions = configOptions;
    }

    /**
     * Performs a request.
     *
     * @param url The specified URL to send the request to.
     * @param requestMethod String representing the request method to use.
     * @param jsonPayload JSON payload as a string
     * @return response or null if an error occurred
     */
    @Nullable
    protected Response performRequest(@Nullable URL url, @NonNull String requestMethod, @NonNull String jsonPayload) {
        if (url == null) {
            Logger.error("Unable to perform request, invalid URL.");
            return null;
        }

        return requestFactory.createRequest(requestMethod, url)
                             .setCredentials(configOptions.appKey, configOptions.appSecret)
                             .setRequestBody(jsonPayload, "application/json")
                             .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                             .execute();
    }

    /**
     * Gets a device url for a given path.
     *
     * @param path The api path.
     * @return The device URL or {@code null} if the URL is invalid.
     */
    @Nullable
    protected URL getDeviceUrl(@NonNull String path) {
        try {
            return new URL(configOptions.deviceUrl + path);
        } catch (MalformedURLException e) {
            Logger.error(e, "Invalid URL: %s", path);
            return null;
        }
    }
}
