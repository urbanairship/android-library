/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * API client for fetching remote data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataApiClient  {

    private AirshipConfigOptions configOptions;
    private RequestFactory requestFactory;
    private static final String REMOTE_DATA_PATH = "api/channels/";

    /**
     * RemoteDataApiClient constructor.
     *
     * @param configOptions The config options.
     */
    RemoteDataApiClient(AirshipConfigOptions configOptions) {
        this(configOptions, new RequestFactory());
    }

    /**
     * RemoteDataApiClient constructor.
     *
     * @param configOptions The config options.
     * @param requestFactory A RequestFactory.
     */
    @VisibleForTesting
    RemoteDataApiClient(AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        this.configOptions = configOptions;
        this.requestFactory = requestFactory;
    }

    /**
     * Executes a remote data request.
     *
     * @param lastModified An optional last-modified timestamp in ISO-8601 format.
     * @return A Response.
     */
    Response fetchRemoteData(String lastModified) {
        URL url = getRemoteDataURL(REMOTE_DATA_PATH);

        Request request = requestFactory.createRequest("GET", url)
                                        .setCredentials(configOptions.getAppKey(), configOptions.getAppSecret());

        if (lastModified != null) {
            request.setHeader("If-Modified-Since", lastModified);
        }

        return request.execute();
    }

    /**
     * Gets a device url for a given path.
     *
     * @param path The API path.
     * @return The device URL or {@code null} if the URL is invalid.
     */
    @Nullable
    protected URL getRemoteDataURL(@NonNull String path) {
        try {
            return new URL(configOptions.remoteDataURL + path);
        } catch (MalformedURLException e) {
            Logger.error("Invalid URL: " + path, e);
            return null;
        }
    }
}
