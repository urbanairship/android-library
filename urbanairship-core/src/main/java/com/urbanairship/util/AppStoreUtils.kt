/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.google.PlayServicesUtils

/**
 * @hide
 */
internal object AppStoreUtils {

    private const val PLAY_STORE_URL = "https://play.google.com/store"
    private const val PLAY_STORE_APP_URL = "https://play.google.com/store/apps/details?id="
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    private const val AMAZON_URL = "amzn://apps/android?p="

    fun getAppStoreIntent(
        context: Context,
        platform: Platform,
        configOptions: AirshipConfigOptions
    ): Intent {
        if (configOptions.appStoreUri != null) {
            val intent = Intent(Intent.ACTION_VIEW, configOptions.appStoreUri)

            val isPlayStoreUrl = configOptions.appStoreUri.toString().startsWith(PLAY_STORE_URL)
            if (isPlayStoreUrl && isPlayStoreAvailable(context)) {
                intent.setPackage(PLAY_STORE_PACKAGE)
            }
            return intent
        }

        val packageName = context.packageName
        return when(platform) {
            Platform.AMAZON -> {
                Intent(Intent.ACTION_VIEW, Uri.parse(AMAZON_URL + packageName))
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_APP_URL + packageName))
                if (isPlayStoreAvailable(context)) {
                    intent.setPackage(PLAY_STORE_PACKAGE)
                }
                return intent
            }
        }
    }

    private fun isPlayStoreAvailable(context: Context): Boolean {
        return PlayServicesUtils.isGooglePlayStoreAvailable(context.applicationContext)
    }
}
