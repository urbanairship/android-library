/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.push.PushProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        List<PushProvider> providers = createProviders();
        if (configOptions.customPushProvider != null) {
            providers.add(0, configOptions.customPushProvider);
        }

        if (providers.isEmpty()) {
            Logger.warn("No push providers found!. Make sure to install either `urbanairship-fcm` or `urbanairship-adm`.");
            return;
        }

        for (PushProvider provider : providers) {
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
     * Creates the list of push providers.
     *
     * @return The list of push providers.
     */
    @NonNull
    private List<PushProvider> createProviders() {
        List<PushProvider> providers = new ArrayList<>();
        List<String> providerClasses = new ArrayList<>();

        for (String className : Arrays.asList(FCM_PUSH_PROVIDER_CLASS, GCM_PUSH_PROVIDER_CLASS, ADM_PUSH_PROVIDER_CLASS)) {
            PushProvider pushProvider = null;
            try {
                Class providerClass = Class.forName(className);
                pushProvider = (PushProvider) providerClass.newInstance();
            } catch (InstantiationException e) {
                Logger.error("Unable to create provider " + className, e);
            } catch (IllegalAccessException e) {
                Logger.error("Unable to create provider " + className, e);
            } catch (ClassNotFoundException e) {
                continue;
            }

            if (pushProvider == null) {
                continue;
            }

            if (pushProvider instanceof AirshipVersionInfo) {
                AirshipVersionInfo versionInfo =  (AirshipVersionInfo)pushProvider;
                Logger.verbose("Found provider: " + pushProvider + " version: " + versionInfo.getPackageVersion());

                if (!UAirship.getVersion().equals(versionInfo.getAirshipVersion())) {
                    Logger.error("Provider: " + pushProvider + " version " + versionInfo.getAirshipVersion() + " does not match the SDK version " + UAirship.getVersion() + ". Make sure all Urban Airship dependencies are the exact same version.");
                    continue;
                }
            }

            providers.add(pushProvider);
            providerClasses.add(className);
        }

        if (providerClasses.contains(FCM_PUSH_PROVIDER_CLASS) && providerClasses.contains(GCM_PUSH_PROVIDER_CLASS)) {
            Logger.error("Both urbanairship-gcm and urbanairship-fcm packages detected. Having both installed is not supported.");
        }

        return providers;
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