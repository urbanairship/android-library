/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import com.urbanairship.UALog.i
import com.urbanairship.base.Supplier
import com.urbanairship.google.PlayServicesUtils
import com.urbanairship.util.PlatformUtils

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DeferredPlatformProvider(
    private val context: Context,
    private val dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val pushProviders: Supplier<PushProviders>
): Provider<Int> {
    override fun get(): Int {
        val existingPlatform = PlatformUtils.parsePlatform(
            dataStore.getInt(PLATFORM_KEY, UAirship.UNKNOWN_PLATFORM)
        )
        return if (existingPlatform != UAirship.UNKNOWN_PLATFORM) {
            existingPlatform
        } else if (privacyManager.isAnyFeatureEnabled) {
            val platform = determinePlatform()
            dataStore.put(PLATFORM_KEY, platform)
            platform
        } else {
            UAirship.UNKNOWN_PLATFORM
        }
    }

    @UAirship.Platform
    private fun determinePlatform(): Int {
        val platform: Int
        val bestProvider = pushProviders.get()!!.getBestProvider()
        if (bestProvider != null) {
            platform = PlatformUtils.parsePlatform(bestProvider.getPlatform())
            i(
                "Setting platform to %s for push provider: %s",
                PlatformUtils.asString(platform),
                bestProvider
            )
        } else if (PlayServicesUtils.isGooglePlayStoreAvailable(context)) {
            i("Google Play Store available. Setting platform to Android.")
            platform = UAirship.ANDROID_PLATFORM
        } else if ("amazon".equals(Build.MANUFACTURER, ignoreCase = true)) {
            i("Build.MANUFACTURER is AMAZON. Setting platform to Amazon.")
            platform = UAirship.AMAZON_PLATFORM
        } else {
            i("Defaulting platform to Android.")
            platform = UAirship.ANDROID_PLATFORM
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
