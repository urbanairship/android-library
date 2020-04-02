/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base class for the NamedUser and Channel Api Clients.
 */
abstract class BaseApiClient {

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestFactory requestFactory;

    BaseApiClient(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.runtimeConfig = runtimeConfig;
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
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(jsonPayload, "application/json")
                             .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                             .execute();
    }
}
