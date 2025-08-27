/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.annotation.RestrictTo
import com.urbanairship.Airship

/**
 * Utility methods for validating the AndroidManifest.xml file.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object ManifestUtils {

    /**
     * Metadata an app can use to disable installing network security provider.
     */
    private const val INSTALL_NETWORK_SECURITY_PROVIDER =
        "com.urbanairship.INSTALL_NETWORK_SECURITY_PROVIDER"

    /**
     * Metadata an app can use to enable local storage.
     */
    public const val ENABLE_LOCAL_STORAGE: String = "com.urbanairship.webview.ENABLE_LOCAL_STORAGE"

    /**
     * Database directory for local storage on Android version prior to API 19.
     */
    public const val LOCAL_STORAGE_DATABASE_DIRECTORY: String =
        "com.urbanairship.webview.localstorage"

    /**
     * Metadata an app can use to enable or disable WebView Safe Browsing.
     */
    private const val ENABLE_WEBVIEW_SAFE_BROWSING = "android.webkit.WebView.EnableSafeBrowsing"

    /**
     * Returns whether the specified permission is granted for the application or not.
     *
     * @param permission Permission to check.
     * @return `true` if the permission is granted, otherwise `false`.
     */
    public fun isPermissionGranted(permission: String): Boolean {
        return PackageManager.PERMISSION_GRANTED == Airship.applicationContext.packageManager
            .checkPermission(permission, Airship.applicationContext.packageName)
    }

    /**
     * Gets the ComponentInfo for an activity
     *
     * @param activity The activity to look up
     * @return The activity's ComponentInfo, or null if the activity
     * is not listed in the manifest
     */
    public fun getActivityInfo(activity: Class<*>): ActivityInfo? {
        if (activity.canonicalName == null) {
            return null
        }

        val componentName = ComponentName(Airship.applicationContext.packageName, activity.canonicalName)
        return try {
            Airship
                .applicationContext
                .packageManager
                .getActivityInfo(componentName, PackageManager.GET_META_DATA)
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * Gets the ApplicationInfo for the application.
     *
     * @return An instance of ApplicationInfo, or null if the info is unavailable.
     */
    public fun getApplicationInfo(): ApplicationInfo? {
        return try {
            Airship
                .applicationContext
                .packageManager
                .getApplicationInfo(Airship.applicationContext.packageName, PackageManager.GET_META_DATA)
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * Helper method to check if local storage should be used.
     *
     * @return `true` if local storage should be used, otherwise `false`.
     */
    public fun shouldEnableLocalStorage(): Boolean {
        val info = getApplicationInfo()
        return info?.metaData?.getBoolean(ENABLE_LOCAL_STORAGE, false) == true
    }

    /**
     * Helper method to check if the network security provider should be installed.
     *
     * @return `true` if the provider should be installed, otherwise `false`.
     */
    public fun shouldInstallNetworkSecurityProvider(): Boolean {
        val info = getApplicationInfo()
        return info?.metaData?.getBoolean(INSTALL_NETWORK_SECURITY_PROVIDER, false) == true
    }

    /**
     * Helper method to check if WebView Safe Browsing should be enabled. Safe browsing is enabled
     * by default, so this method checks for the presence of a metadata tag that disables it.
     *
     * @return `true` if safe browsing is not disabled via the manifest, otherwise `false`.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun isWebViewSafeBrowsingEnabled(): Boolean {
        val metadata = getApplicationInfo()?.metaData ?: return true
        if (!metadata.containsKey(ENABLE_WEBVIEW_SAFE_BROWSING)) {
            return true
        }

        return metadata.getBoolean(ENABLE_WEBVIEW_SAFE_BROWSING, true)
    }
}
