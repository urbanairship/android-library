/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
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
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.UAStringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * API client for fetching remote data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataApiClient {

    private static final String REMOTE_DATA_PATH = "api/remote-data/app/";
    private static final String SDK_VERSION_QUERY_PARAM = "sdk_version";
    // ISO 639-2 two digit country code
    private static final String COUNTRY_QUERY_PARAM = "country";
    // ISO 3166-2 two digit language code
    private static final String LANGUAGE_QUERY_PARAM = "language";


    private static final String AMAZON = "amazon";
    private static final String ANDROID = "android";

    private final AirshipConfigOptions configOptions;
    private final RequestFactory requestFactory;
    private final Context context;
    private final LocaleManager localeManager;

    @Nullable
    private URL url;

    /**
     * RemoteDataApiClient constructor.
     *
     * @param context The application context.
     * @param configOptions The config options.
     */
    RemoteDataApiClient(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        this(context, configOptions, RequestFactory.DEFAULT_REQUEST_FACTORY, LocaleManager.shared());
    }

    /**
     * RemoteDataApiClient constructor.
     *
     * @param context The application context.
     * @param configOptions The config options.
     * @param requestFactory A RequestFactory.
     * @param localeManager The locale manager.
     */
    @VisibleForTesting
    RemoteDataApiClient(@NonNull Context context, @NonNull AirshipConfigOptions configOptions,
                        @NonNull RequestFactory requestFactory, @NonNull LocaleManager localeManager) {
        this.configOptions = configOptions;
        this.requestFactory = requestFactory;
        this.context = context;
        this.localeManager = localeManager;
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

        // api/remote-data/app/{appkey}/{platform}?sdk_version={version}&language={language}&country={country}

        try {
            Uri.Builder builder = Uri.parse(configOptions.remoteDataURL)
                    .buildUpon()
                    .appendEncodedPath(REMOTE_DATA_PATH)
                    .appendPath(configOptions.getAppKey())
                    .appendPath(UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? AMAZON : ANDROID)
                    .appendQueryParameter(SDK_VERSION_QUERY_PARAM, UAirship.getVersion());

            Locale locale = localeManager.getDefaultLocale(context);
            if (!UAStringUtil.isEmpty(locale.getLanguage())) {
                builder.appendQueryParameter(LANGUAGE_QUERY_PARAM, locale.getLanguage());
            }

            if (!UAStringUtil.isEmpty(locale.getCountry())) {
                builder.appendQueryParameter(COUNTRY_QUERY_PARAM, locale.getCountry());
            }

            url = new URL(builder.build().toString());
        } catch (MalformedURLException e) {
            Logger.error(e, "Invalid URL.");
            return null;
        }

        return url;
    }
}
