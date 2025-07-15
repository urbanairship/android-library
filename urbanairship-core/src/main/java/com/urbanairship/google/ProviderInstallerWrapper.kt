/* Copyright Airship and Contributors */
package com.urbanairship.google

import android.content.Context
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller

/**
 * Wrapper around GMS ProviderInstaller.
 */
internal object ProviderInstallerWrapper {

    fun installIfNeeded(context: Context): NetworkProviderInstaller.Result {
        try {
            ProviderInstaller.installIfNeeded(context)
            return NetworkProviderInstaller.Result.PROVIDER_INSTALLED
        } catch (e: GooglePlayServicesRepairableException) {
            return NetworkProviderInstaller.Result.PROVIDER_RECOVERABLE_ERROR
        } catch (e: GooglePlayServicesNotAvailableException) {
            return NetworkProviderInstaller.Result.PROVIDER_ERROR
        }
    }
}
