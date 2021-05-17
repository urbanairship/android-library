/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.os.Build;

import com.urbanairship.base.Supplier;
import com.urbanairship.config.PlatformProvider;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.PlatformUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DeferredPlatformProvider implements PlatformProvider {

    /**
     * Push provider class preference key.
     */
    private static final String PLATFORM_KEY = "com.urbanairship.application.device.PLATFORM";

    private final PreferenceDataStore dataStore;
    private final Supplier<PushProviders> pushProviders;
    private final PrivacyManager privacyManager;
    private final Context context;

    public DeferredPlatformProvider(@NonNull Context context,
                                    @NonNull PreferenceDataStore dataStore,
                                    @NonNull PrivacyManager privacyManager,
                                    @NonNull Supplier<PushProviders> pushProviders) {
        this.dataStore = dataStore;
        this.privacyManager = privacyManager;
        this.pushProviders = pushProviders;
        this.context = context.getApplicationContext();
    }

    @UAirship.Platform
    public int getPlatform() {
        @UAirship.Platform
        int existingPlatform = PlatformUtils.parsePlatform(dataStore.getInt(PLATFORM_KEY, UAirship.UNKNOWN_PLATFORM));

        if (existingPlatform != UAirship.UNKNOWN_PLATFORM) {
            return existingPlatform;
        } else if (privacyManager.isAnyFeatureEnabled()) {
            int platform = determinePlatform();
            dataStore.put(PLATFORM_KEY, platform);
            return platform;
        } else {
            return UAirship.UNKNOWN_PLATFORM;
        }
    }

    @UAirship.Platform
    private int determinePlatform() {
        int platform;

        PushProvider bestProvider = pushProviders.get().getBestProvider();
        if (bestProvider != null) {
            platform = PlatformUtils.parsePlatform(bestProvider.getPlatform());
            Logger.info("Setting platform to %s for push provider: %s", PlatformUtils.asString(platform), bestProvider);
        } else if (PlayServicesUtils.isGooglePlayStoreAvailable(context)) {
            Logger.info("Google Play Store available. Setting platform to Android.");
            platform = UAirship.ANDROID_PLATFORM;
        } else if ("amazon".equalsIgnoreCase(Build.MANUFACTURER)) {
            Logger.info("Build.MANUFACTURER is AMAZON. Setting platform to Amazon.");
            platform = UAirship.AMAZON_PLATFORM;
        } else {
            Logger.info("Defaulting platform to Android.");
            platform = UAirship.ANDROID_PLATFORM;
        }

        return platform;
    }

}
