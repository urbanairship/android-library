/* Copyright Airship and Contributors */
package com.urbanairship.google

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.urbanairship.UALog
import com.urbanairship.google.GooglePlayServicesUtilWrapper.isUserRecoverableError

/**
 * A utility class to help verify and resolve Google Play services issues.
 */
public object PlayServicesUtils {

    private const val GOOGLE_PLAY_STORE_PACKAGE_OLD = "com.google.market"
    private const val GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * Value of [com.google.android.gms.common.ConnectionResult.SUCCESS].
     */
    private const val CONNECTION_SUCCESS = 0

    /**
     * Error code returned by [PlayServicesUtils.isGooglePlayServicesDependencyAvailable]
     * when the Google Play services dependency is missing.
     */
    public const val MISSING_PLAY_SERVICE_DEPENDENCY: Int = -1

    private var isGooglePlayServicesDependencyAvailable: Boolean? = null
    public var isFusedLocationDependencyAvailable: Boolean? = null
        /**
         * Checks if Google Play services dependency is available for Fused Location.
         *
         * @return `true` if available, otherwise `false`.
         */
        get() {
            if (field != null) {
                return field
            }

            if (!isGooglePlayServicesDependencyAvailable()) {
                field = false
                return field
            }

            try {
                Class.forName("com.google.android.gms.location.FusedLocationProviderClient")
                field = true
            } catch (e: ClassNotFoundException) {
                field = false
            }

            return field
        }
        private set

    private var isGooglePlayStoreAvailable: Boolean? = null
    public var isGoogleAdsDependencyAvailable: Boolean? = null
        /**
         * Checks if Google Play services dependency is available for advertising ID tracking.
         *
         * @return `true` if available, otherwise `false`.
         */
        get() {
            if (field != null) {
                return field
            }

            if (!isGooglePlayServicesDependencyAvailable()) {
                field = false
                return field
            }

            try {
                Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
                field = true
            } catch (e: ClassNotFoundException) {
                field = false
            }

            return field
        }
        private set

    /**
     * Checks and handles any user recoverable Google Play services errors.
     *
     * If a user recoverable error is encountered, a [com.urbanairship.google.PlayServicesErrorActivity]
     * will be launched to display any resolution dialog provided by Google Play
     * services.
     *
     * @param context The application context.
     */
    @JvmStatic
    public fun handleAnyPlayServicesError(context: Context) {
        if (!isGooglePlayServicesDependencyAvailable()) {
            return
        }

        val errorCode = try {
            GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(context)
        } catch (e: IllegalStateException) {
            UALog.e(e, "Google Play services developer error.")
            return
        }

        if (errorCode == CONNECTION_SUCCESS) {
            return
        }

        if (isUserRecoverableError(errorCode)) {
            UALog.d("Launching Play Services Activity to resolve error.")
            try {
                context.startActivity(Intent(context, PlayServicesErrorActivity::class.java))
            } catch (e: ActivityNotFoundException) {
                UALog.e(e)
            }
        } else {
            UALog.i("Error %s is not user recoverable.", errorCode)
        }
    }

    /**
     * Verifies that Google Play services dependency is available and the Google
     * Play services version required for the application is installed and enabled
     * on the device.
     *
     *
     * This method is a wrapper around
     * [com.google.android.gms.common.GooglePlayServicesUtil.isGooglePlayServicesAvailable]
     * but with an additional check if the dependency is also available.
     *
     * @param context The application context.
     * @return [MISSING_PLAY_SERVICE_DEPENDENCY] if Google Play services dependency is missing,
     * or the errorCode returned by
     * [com.google.android.gms.common.GooglePlayServicesUtil.isGooglePlayServicesAvailable]
     */
    @JvmStatic
    public fun isGooglePlayServicesAvailable(context: Context): Int {
        return if (isGooglePlayServicesDependencyAvailable()) {
            GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(context)
        } else {
            MISSING_PLAY_SERVICE_DEPENDENCY
        }
    }

    /**
     * Checks if Google Play services dependency is available.
     *
     * @return `true` if available, otherwise `false`.
     */
    public fun isGooglePlayServicesDependencyAvailable(): Boolean {
        val evaluated = isGooglePlayServicesDependencyAvailable
        if (evaluated != null) {
            return evaluated
        }

        // Play Services
        val result = try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        isGooglePlayServicesDependencyAvailable = result

        return result
    }

    /**
     * Checks if the Google Play Store package is installed on the device.
     *
     * @param context The application context.
     * @return `true` if Google Play Store package is installed on the device,
     * otherwise `false`
     */
    @JvmStatic
    public fun isGooglePlayStoreAvailable(context: Context): Boolean {

        return isGooglePlayStoreAvailable ?: run {
            val result = isPackageAvailable(context, GOOGLE_PLAY_STORE_PACKAGE)
                    || isPackageAvailable(context, GOOGLE_PLAY_STORE_PACKAGE_OLD)
            isGooglePlayStoreAvailable = result
            result
        }
    }

    /**
     * Checks if a given package is installed on the device.
     *
     * @param context The application context.
     * @param packageName The name of the package as a string.
     * @return `true` if the given package is installed on the device,
     * otherwise `false`
     */
    private fun isPackageAvailable(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
