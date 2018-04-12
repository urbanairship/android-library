/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.push.PushProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads push providers.
 */
class PushProviders {

    private static final String GCM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.gcm.GcmPushProvider";
    private static final String FCM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.fcm.FcmPushProvider";
    private static final String ADM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.adm.AdmPushProvider";

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
        boolean pushProviderFound = false;

        for (String className : Arrays.asList(FCM_PUSH_PROVIDER_CLASS, GCM_PUSH_PROVIDER_CLASS, ADM_PUSH_PROVIDER_CLASS)) {
            PushProvider provider = createProvider(className);
            if (provider == null) {
                continue;
            }

            pushProviderFound = true;

            if (!provider.isSupported(context, configOptions)) {
                continue;
            }

            supportedProviders.add(provider);
            if (provider.isAvailable(context)) {
                availableProviders.add(provider);
            }
        }

        if (!pushProviderFound) {
            Logger.error("No push providers found!");
        }
    }


    /**
     * Creates a provider from a class name.
     *
     * @param className The class name.
     * @return The push provider or null if the provider does not exist.
     */
    @Nullable
    private PushProvider createProvider(@NonNull String className) {
        try {
            Class providerClass = Class.forName(className);
            return (PushProvider) providerClass.newInstance();
        } catch (InstantiationException e) {
            Logger.error("Unable to create provider " + className, e);
        } catch (IllegalAccessException e) {
            Logger.error("Unable to create provider " + className, e);
        } catch (ClassNotFoundException e) {
            // Normal
        }

        return null;
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