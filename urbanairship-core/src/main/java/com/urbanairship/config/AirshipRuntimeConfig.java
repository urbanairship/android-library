/* Copyright Airship and Contributors */

package com.urbanairship.config;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;

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
    @UAirship.Platform
    private final int platform;

    /**
     * Default constructor.
     *
     * @param urlConfigProvider The URL config provider.
     * @param configOptions The config options.
     * @param platform The platform.
     */
    public AirshipRuntimeConfig(@NonNull AirshipUrlConfigProvider urlConfigProvider,
                                @NonNull AirshipConfigOptions configOptions,
                                @UAirship.Platform int platform) {
        this.platform = platform;
        this.configOptions = configOptions;
        this.urlConfigProvider = urlConfigProvider;
    }

    /**
     * Gets the platform.
     *
     * @return The platform.
     */
    @UAirship.Platform
    public int getPlatform() {
        return platform;
    }

    /**
     * Gets the URL config.
     *
     * @return The URL config.
     */
    public AirshipUrlConfig getUrlConfig() {
        return urlConfigProvider.getConfig();
    }

    /**
     * Gets the Airship config options.
     *
     * @return The config options.
     */
    public AirshipConfigOptions getConfigOptions() {
        return configOptions;
    }

}
