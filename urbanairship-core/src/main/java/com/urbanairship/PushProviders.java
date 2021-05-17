/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;

import com.urbanairship.base.Supplier;
import com.urbanairship.push.PushProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Loads push providers.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PushProviders {

    private static final String FCM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.fcm.FcmPushProvider";
    private static final String ADM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.adm.AdmPushProvider";
    private static final String HMS_PUSH_PROVIDER_CLASS = "com.urbanairship.push.hms.HmsPushProvider";

    private final List<PushProvider> supportedProviders = new ArrayList<>();
    private final List<PushProvider> availableProviders = new ArrayList<>();
    private final AirshipConfigOptions airshipConfigOptions;

    @VisibleForTesting
    protected PushProviders(@NonNull AirshipConfigOptions config) {
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

    static Supplier<PushProviders> lazyLoader(@NonNull Context context, @NonNull AirshipConfigOptions config) {
        return new LazyLoader(context, config);
    }

    /**
     * Loads all the plugins that are currently supported by the device.
     */
    private void init(@NonNull Context context) {
        List<PushProvider> providers = createProviders();

        if (providers.isEmpty()) {
            Logger.warn("No push providers found!. Make sure to install either `urbanairship-fcm` or `urbanairship-adm`.");
            return;
        }

        for (PushProvider provider : providers) {
            if (!isValid(provider)) {
                continue;
            }

            if (!provider.isSupported(context)) {
                continue;
            }

            supportedProviders.add(provider);
            if (provider.isAvailable(context)) {
                availableProviders.add(provider);
            }
        }
    }

    private boolean isValid(PushProvider provider) {
        if (provider instanceof AirshipVersionInfo) {
            AirshipVersionInfo versionInfo = (AirshipVersionInfo) provider;
            if (!UAirship.getVersion().equals(versionInfo.getAirshipVersion())) {
                Logger.error("Provider: %s version %s does not match the SDK version %s. Make sure all Airship dependencies are the same version.", provider, versionInfo.getAirshipVersion(), UAirship.getVersion());
                return false;
            }
        }

        switch (provider.getDeliveryType()) {
            case PushProvider.ADM_DELIVERY_TYPE:
                if (provider.getPlatform() != UAirship.AMAZON_PLATFORM) {
                    Logger.error("Invalid Provider: %s. ADM delivery is only available for Amazon platforms.", provider);
                    return false;
                }
                break;
            case PushProvider.FCM_DELIVERY_TYPE:
            case PushProvider.HMS_DELIVERY_TYPE:
                if (provider.getPlatform() != UAirship.ANDROID_PLATFORM) {
                    Logger.error("Invalid Provider: %s. %s delivery is only available for Android platforms.", provider.getDeliveryType(), provider);
                    return false;
                }
                break;
        }

        return true;
    }

    /**
     * Creates the list of push providers.
     *
     * @return The list of push providers.
     */
    @NonNull
    private List<PushProvider> createProviders() {
        List<PushProvider> providers = new ArrayList<>();

        if (airshipConfigOptions.customPushProvider != null) {
            providers.add(airshipConfigOptions.customPushProvider);
        }

        for (String className : createAllowedProviderClassList()) {
            PushProvider pushProvider = null;
            try {
                Class providerClass = Class.forName(className);
                pushProvider = (PushProvider) providerClass.newInstance();
                Logger.verbose("Found provider: %s", pushProvider);
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

            providers.add(pushProvider);
        }

        return providers;
    }

    @NonNull
    public List<PushProvider> getAvailableProviders() {
        return Collections.unmodifiableList(availableProviders);
    }

    /**
     * Gets the best provider for the specified platform.
     *
     * @param platform The specified platform.
     * @return The best provider for the platform, or {@code null} if no provider is found.
     */
    @Nullable
    public PushProvider getBestProvider(@UAirship.Platform int platform) {
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
    public PushProvider getProvider(@UAirship.Platform int platform, @NonNull String providerClass) {
        for (PushProvider pushProvider : supportedProviders) {
            if (platform == pushProvider.getPlatform() && providerClass.equals(pushProvider.getClass().toString())) {
                return pushProvider;
            }
        }

        return null;
    }

    private List<String> createAllowedProviderClassList() {
        List<String> providers = new ArrayList<>();
        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.FCM_TRANSPORT)) {
            providers.add(FCM_PUSH_PROVIDER_CLASS);
        }

        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.ADM_TRANSPORT)) {
            providers.add(ADM_PUSH_PROVIDER_CLASS);
        }

        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.HMS_TRANSPORT)) {
            providers.add(HMS_PUSH_PROVIDER_CLASS);
        }

        return providers;
    }

    private static class LazyLoader implements Supplier<PushProviders> {

        private final Context context;
        private final AirshipConfigOptions config;
        PushProviders pushProviders = null;

        public LazyLoader(Context context, AirshipConfigOptions config) {
            this.context = context;
            this.config = config;
        }

        @Nullable
        @Override
        public synchronized PushProviders get() {
            if (pushProviders == null) {
                pushProviders = PushProviders.load(context, config);
            }
            return pushProviders;
        }
    }

}
