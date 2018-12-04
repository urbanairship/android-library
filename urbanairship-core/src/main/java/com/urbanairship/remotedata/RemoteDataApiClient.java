/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
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
public class RemoteDataApiClient {

    private static final String REMOTE_DATA_PATH = "api/remote-data/app/";
    private static final String SDK_VERSION_QUERY_PARAM = "sdk_version";

    private static final String AMAZON = "amazon";
    private static final String ANDROID = "android";

    private final AirshipConfigOptions configOptions;
    private final RequestFactory requestFactory;

    @Nullable
    private URL url;

    /**
     * RemoteDataApiClient constructor.
     *
     * @param configOptions The config options.
     */
    RemoteDataApiClient(AirshipConfigOptions configOptions) {
        this(configOptions, RequestFactory.DEFAULT_REQUEST_FACTORY);
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
    @Nullable
    Response fetchRemoteData(@Nullable String lastModified) {
        URL url = getRemoteDataURL();

        if (url == null) {
            return null;
        }


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
     * @return The device URL or {@code null} if the URL is invalid.
     */
    @Nullable
    private URL getRemoteDataURL() {
        if (url != null) {
            return url;
        }

        // api/remote-data/app/{appkey}/{platform}?sdk_version={version}

        try {
            Uri uri = Uri.parse(configOptions.remoteDataURL)
                         .buildUpon()
                         .appendEncodedPath(REMOTE_DATA_PATH)
                         .appendPath(configOptions.getAppKey())
                         .appendPath(UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? AMAZON : ANDROID)
                         .appendQueryParameter(SDK_VERSION_QUERY_PARAM, UAirship.getVersion())
                         .build();

            url = new URL(uri.toString());
        } catch (MalformedURLException e) {
            Logger.error(e, "Invalid URL.");
            return null;
        }

        return url;
    }
}
