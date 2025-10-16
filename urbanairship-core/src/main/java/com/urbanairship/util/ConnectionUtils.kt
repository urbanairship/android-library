/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import androidx.annotation.WorkerThread
import com.urbanairship.UALog
import com.urbanairship.google.NetworkProviderInstaller
import java.io.IOException
import java.net.URL
import java.net.URLConnection

/**
 * Connection utils.
 *
 * @hide
 */
internal object ConnectionUtils {

    private var skipInstall = false
    private var isInstalled = false

    /**
     * Opens a URL connection but tries to first install the network provider through Google Play
     * services.
     *
     * @param context The application context.
     * @param url The URL.
     * @return The URLConnection.
     * @throws IOException
     */
    @WorkerThread
    @Throws(IOException::class)
    fun openSecureConnection(context: Context, url: URL): URLConnection {
        installProvider(context)
        return url.openConnection()
    }

    @WorkerThread
    @Synchronized
    private fun installProvider(context: Context): Boolean {
        if (skipInstall) {
            return isInstalled
        }

        if (!ManifestUtils.shouldInstallNetworkSecurityProvider(context)) {
            skipInstall = true
            return isInstalled
        }

        when (NetworkProviderInstaller.installSecurityProvider(context)) {
            NetworkProviderInstaller.Result.PROVIDER_INSTALLED -> {
                UALog.i("Network Security Provider installed.")
                skipInstall = true
                isInstalled = true
            }
            NetworkProviderInstaller.Result.PROVIDER_RECOVERABLE_ERROR -> {
                UALog.i("Network Security Provider failed to install with a recoverable error.")
            }
            NetworkProviderInstaller.Result.PROVIDER_ERROR -> {
                UALog.i("Network Security Provider failed to install.")
                skipInstall = true
            }
        }

        return isInstalled
    }
}
