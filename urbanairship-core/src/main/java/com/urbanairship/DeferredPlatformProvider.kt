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
): Provider<UAirship.Platform> {
    override fun get(): UAirship.Platform {
        val existingPlatform = UAirship.Platform.fromRawValue(
            rawValue = dataStore.getInt(PLATFORM_KEY, UAirship.Platform.UNKNOWN.rawValue)
        )
        return if (existingPlatform != UAirship.Platform.UNKNOWN) {
            existingPlatform
        } else if (privacyManager.isAnyFeatureEnabled) {
            val platform = determinePlatform()
            dataStore.put(PLATFORM_KEY, platform.rawValue)
            platform
        } else {
            UAirship.Platform.UNKNOWN
        }
    }

    private fun determinePlatform(): UAirship.Platform {
        val platform: UAirship.Platform
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
            platform = UAirship.Platform.ANDROID
        } else if ("amazon".equals(Build.MANUFACTURER, ignoreCase = true)) {
            UALog.i("Build.MANUFACTURER is AMAZON. Setting platform to Amazon.")
            platform = UAirship.Platform.AMAZON
        } else {
            UALog.i("Defaulting platform to Android.")
            platform = UAirship.Platform.ANDROID
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
