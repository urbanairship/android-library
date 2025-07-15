/* Copyright Airship and Contributors */
package com.urbanairship.google

import android.content.Context
import androidx.annotation.WorkerThread

/**
 * GMS network provider installer.
 *
 * @hide
 */
internal object NetworkProviderInstaller {

    private var isProviderInstallerDependencyAvailable: Boolean? = null
        /**
         * Checks if Google Play services dependency is available for Provider Installer.
         *
         * @return `true` if available, otherwise `false`.
         */
        get() {
            if (field != null) {
                return field
            }

            if (!PlayServicesUtils.isGooglePlayServicesDependencyAvailable()) {
                field = false
                return field
            }

            try {
                Class.forName("com.google.android.gms.security.ProviderInstaller")
                field = true
            } catch (e: ClassNotFoundException) {
                field = false
            }

            return field
        }

    /**
     * Tries to install the provider.
     *
     * @param context The context.
     * @return The result.
     */
    @WorkerThread
    internal fun installSecurityProvider(context: Context): Result {
        if (isProviderInstallerDependencyAvailable != true) {
            return Result.PROVIDER_ERROR
        }

        return ProviderInstallerWrapper.installIfNeeded(context)
    }

    internal enum class Result {
        /**
         * Provider installed.
         */
        PROVIDER_INSTALLED,
        /**
         * Provider failed to install, but should be retried.
         */
        PROVIDER_RECOVERABLE_ERROR,
        /**
         * Provider failed to install.
         */
        PROVIDER_ERROR
    }
}
