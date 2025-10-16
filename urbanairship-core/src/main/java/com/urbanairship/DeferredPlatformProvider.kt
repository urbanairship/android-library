/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import com.urbanairship.google.PlayServicesUtils

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DeferredPlatformProvider(
    private val context: Context,
    private val dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val pushProviders: () -> PushProviders
): Provider<Platform> {
    override fun get(): Platform {
        val existingPlatform = Platform.fromRawValue(
            rawValue = dataStore.getInt(PLATFORM_KEY, Platform.UNKNOWN.rawValue)
        )
        return if (existingPlatform != Platform.UNKNOWN) {
            existingPlatform
        } else if (privacyManager.isAnyFeatureEnabled) {
            val platform = determinePlatform()
            dataStore.put(PLATFORM_KEY, platform.rawValue)
            platform
        } else {
            Platform.UNKNOWN
        }
    }

    private fun determinePlatform(): Platform {
        val platform: Platform
        val bestProvider = pushProviders.invoke().bestProvider
        if (bestProvider != null) {
            platform = bestProvider.platform
            UALog.i(
                "Setting platform to %s for push provider: %s",
                platform.stringValue,
                bestProvider
            )
        } else if (PlayServicesUtils.isGooglePlayStoreAvailable(context)) {
            UALog.i("Google Play Store available. Setting platform to Android.")
            platform = Platform.ANDROID
        } else if ("amazon".equals(Build.MANUFACTURER, ignoreCase = true)) {
            UALog.i("Build.MANUFACTURER is AMAZON. Setting platform to Amazon.")
            platform = Platform.AMAZON
        } else {
            UALog.i("Defaulting platform to Android.")
            platform = Platform.ANDROID
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
