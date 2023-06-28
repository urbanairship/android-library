/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;

import com.urbanairship.UALog;
import com.urbanairship.google.NetworkProviderInstaller;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * Connection utils.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ConnectionUtils {

    private static boolean skipInstall = false;
    private static boolean isInstalled = false;

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
    @NonNull
    public static URLConnection openSecureConnection(@NonNull Context context, @NonNull URL url) throws IOException {
        installProvider(context);
        return url.openConnection();
    }

    @WorkerThread
    private synchronized static boolean installProvider(@NonNull Context context) {
        if (skipInstall) {
            return isInstalled;
        }

        if (!ManifestUtils.shouldInstallNetworkSecurityProvider()) {
            skipInstall = true;
            return isInstalled;
        }

        int result = NetworkProviderInstaller.installSecurityProvider(context);
        switch (result) {
            case NetworkProviderInstaller.PROVIDER_INSTALLED:
                UALog.i("Network Security Provider installed.");
                skipInstall = true;
                isInstalled = true;
                break;
            case NetworkProviderInstaller.PROVIDER_ERROR:
                UALog.i("Network Security Provider failed to install.");
                skipInstall = true;
                break;
            case NetworkProviderInstaller.PROVIDER_RECOVERABLE_ERROR:
                UALog.i("Network Security Provider failed to install with a recoverable error.");
                break;
        }

        return isInstalled;
    }
}
