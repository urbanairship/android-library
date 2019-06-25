/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.push.PushProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads push providers.
 */
class PushProviders {

    private static final String FCM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.fcm.FcmPushProvider";
    private static final String ADM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.adm.AdmPushProvider";

    private final List<PushProvider> supportedProviders = new ArrayList<>();
    private final List<PushProvider> availableProviders = new ArrayList<>();
    private final AirshipConfigOptions airshipConfigOptions;

    private PushProviders(@NonNull AirshipConfigOptions config) {
        this.airshipConfigOptions = config;
    }

    /**
     * Factory method to load push providers.
     *
     * @param context The application context.
     * @param config The airship config.
     * @return A PushProviders class with the loaded providers.
     */
    @NonNull
    static PushProviders load(@NonNull Context context, @NonNull AirshipConfigOptions config) {
        PushProviders providers = new PushProviders(config);
        providers.init(context);
        return providers;
    }

    /**
     * Loads all the plugins that are currently supported by the device.
     */
    private void init(@NonNull Context context) {
        List<PushProvider> providers = createProviders();
        if (airshipConfigOptions.customPushProvider != null) {
            providers.add(0, airshipConfigOptions.customPushProvider);
        }

        if (providers.isEmpty()) {
            Logger.warn("No push providers found!. Make sure to install either `urbanairship-fcm` or `urbanairship-adm`.");
            return;
        }

        for (PushProvider provider : providers) {
            if (!provider.isSupported(context)) {
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

        for (String className : createAllowedProviderClassList()) {
            PushProvider pushProvider = null;
            try {
                Class providerClass = Class.forName(className);
                pushProvider = (PushProvider) providerClass.newInstance();
            } catch (InstantiationException e) {
                Logger.error(e, "Unable to create provider %s", className);
            } catch (IllegalAccessException e) {
                Logger.error(e, "Unable to create provider %s", className);
            } catch (ClassNotFoundException e) {
                continue;
            }

            if (pushProvider == null) {
                continue;
            }

            if (pushProvider instanceof AirshipVersionInfo) {
                AirshipVersionInfo versionInfo = (AirshipVersionInfo) pushProvider;
                Logger.verbose("Found provider: %s version: %s", pushProvider, versionInfo.getPackageVersion());

                if (!UAirship.getVersion().equals(versionInfo.getAirshipVersion())) {
                    Logger.error("Provider: %s version %s does not match the SDK version %s. Make sure all Airship dependencies are the exact same version.", pushProvider, versionInfo.getAirshipVersion(), UAirship.getVersion());
                    continue;
                }
            }

            providers.add(pushProvider);
            providerClasses.add(className);
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

    public List<String> createAllowedProviderClassList() {
        List<String> providers = new ArrayList<>();
        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.FCM_TRANSPORT)) {
            providers.add(FCM_PUSH_PROVIDER_CLASS);
        }

        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.ADM_TRANSPORT)) {
            providers.add(ADM_PUSH_PROVIDER_CLASS);
        }

        return providers;
    }

}