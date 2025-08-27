/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import com.urbanairship.base.Supplier
import com.urbanairship.google.PlayServicesUtils

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DeferredPlatformProvider(
    private val context: Context,
    private val dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val pushProviders: Supplier<PushProviders>
): Provider<Airship.Platform> {
    override fun get(): Airship.Platform {
        val existingPlatform = Airship.Platform.fromRawValue(
            rawValue = dataStore.getInt(PLATFORM_KEY, Airship.Platform.UNKNOWN.rawValue)
        )
        return if (existingPlatform != Airship.Platform.UNKNOWN) {
            existingPlatform
        } else if (privacyManager.isAnyFeatureEnabled) {
            val platform = determinePlatform()
            dataStore.put(PLATFORM_KEY, platform.rawValue)
            platform
        } else {
            Airship.Platform.UNKNOWN
        }
    }

    private fun determinePlatform(): Airship.Platform {
        val platform: Airship.Platform
        val bestProvider = pushProviders.get()?.bestProvider
        if (bestProvider != null) {
            platform = bestProvider.platform
            UALog.i(
                "Setting platform to %s for push provider: %s",
                platform.stringValue,
                bestProvider
            )
        } else if (PlayServicesUtils.isGooglePlayStoreAvailable(context)) {
            UALog.i("Google Play Store available. Setting platform to Android.")
            platform = Airship.Platform.ANDROID
        } else if ("amazon".equals(Build.MANUFACTURER, ignoreCase = true)) {
            UALog.i("Build.MANUFACTURER is AMAZON. Setting platform to Amazon.")
            platform = Airship.Platform.AMAZON
        } else {
            UALog.i("Defaulting platform to Android.")
            platform = Airship.Platform.ANDROID
        }
        return platform
    }

    companion object {
        /**
         * Push provider class preference key.
         */
        private const val PLATFORM_KEY = "com.urbanairship.application.device.PLATFORM"
    }
}
