/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.push.PushProvider;
import com.urbanairship.push.adm.AdmPushProvider;
import com.urbanairship.push.gcm.GcmPushProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads push providers.
 */
class PushProviders {

    private List<PushProvider> supportedProviders = new ArrayList<>();
    private List<PushProvider> availableProviders = new ArrayList<>();

    private PushProviders() {}

    /**
     * Factory method to load push providers.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @return A PushProviders class with the loaded providers.
     */
    static PushProviders load(Context context, AirshipConfigOptions configOptions) {
        PushProviders providers = new PushProviders();
        providers.init(context, configOptions);
        return providers;
    }

    /**
     * Loads all the plugins that are currently supported by the device.
     */
    private void init(Context context, AirshipConfigOptions configOptions) {
        for (PushProvider provider : Arrays.asList(new GcmPushProvider(), new AdmPushProvider())) {
            if (!provider.isSupported(context, configOptions)) {
                continue;
            }

            supportedProviders.add(provider);
            if (provider.isAvailable(context)) {
                availableProviders.add(provider);
            }
        }
    }

    /**
     * Gets the best provider for the specified platform.
     *
     * @param platform The specified platform.
     * @return The best provider for the platform, or {@code null} if no provider is found.
     */
    @Nullable
    PushProvider getBestProvider(@UAirship.Platform int platform) {
        for (PushProvider provider : availableProviders) {
            if (provider.getPlatform() == platform) {
                return provider;
            }
        }

        for (PushProvider provider : supportedProviders) {
            if (provider.getPlatform() == platform) {
                return provider;
            }
        }

        return null;
    }

    /**
     * Gets the best provider.
     *
     * @return The best provider, or {@code null} if no provider is available.
     */
    @Nullable
    PushProvider getBestProvider() {
        if (!availableProviders.isEmpty()) {
            return availableProviders.get(0);
        }

        if (!supportedProviders.isEmpty()) {
            return supportedProviders.get(0);
        }

        return null;
    }

    /**
     * Gets the provider with the specified class and platform.
     *
     * @param platform The provider platform.
     * @param providerClass The provider's class.
     * @return The provider or {@code null} if the specified provider is not available.
     */
    @Nullable
    PushProvider getProvider(@UAirship.Platform int platform, @NonNull String providerClass) {
        for (PushProvider pushProvider : supportedProviders) {
            if (platform == pushProvider.getPlatform() && providerClass.equals(pushProvider.getClass().toString())) {
                return pushProvider;
            }
        }

        return null;
    }
}
