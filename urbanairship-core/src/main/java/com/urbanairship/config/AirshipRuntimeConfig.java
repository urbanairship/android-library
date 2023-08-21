/* Copyright Airship and Contributors */

package com.urbanairship.config;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestSession;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Airship runtime config.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipRuntimeConfig {

    private final AirshipUrlConfigProvider urlConfigProvider;
    private final AirshipConfigOptions configOptions;
    private final PlatformProvider platformProvider;
    private final RequestSession requestSession;

    /**
     * Default constructor.
     *
     * @param platformProvider The platform provider.
     * @param configOptions The config options.
     * @param urlConfigProvider The URL config provider.
     */
    public AirshipRuntimeConfig(@NonNull PlatformProvider platformProvider,
                                @NonNull AirshipConfigOptions configOptions,
                                @NonNull AirshipUrlConfigProvider urlConfigProvider,
                                @NonNull RequestSession requestSession) {
        this.platformProvider = platformProvider;
        this.configOptions = configOptions;
        this.urlConfigProvider = urlConfigProvider;
        this.requestSession = requestSession;
    }

    /**
     * Gets the platform.
     *
     * @return The platform.
     */
    @UAirship.Platform
    public int getPlatform() {
        return platformProvider.getPlatform();
    }

    /**
     * Gets the URL config.
     *
     * @return The URL config.
     */
    @NonNull
    public AirshipUrlConfig getUrlConfig() {
        return urlConfigProvider.getConfig();
    }

    /**
     * Gets the Airship config options.
     *
     * @return The config options.
     */
    @NonNull
    public AirshipConfigOptions getConfigOptions() {
        return configOptions;
    }

    /**
     * Gets the request session.
     * @return The request session.
     */
    @NonNull
    public RequestSession getRequestSession() {
        return requestSession;
    }
}
