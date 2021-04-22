/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.os.Build;

import com.urbanairship.Logger;
import com.urbanairship.google.NetworkProviderInstaller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
                Logger.info("Network Security Provider installed.");
                skipInstall = true;
                isInstalled = true;
                break;
            case NetworkProviderInstaller.PROVIDER_ERROR:
                Logger.info("Network Security Provider failed to install.");
                skipInstall = true;
                break;
            case NetworkProviderInstaller.PROVIDER_RECOVERABLE_ERROR:
                Logger.info("Network Security Provider failed to install with a recoverable error.");
                break;
        }

        return isInstalled;
    }
}
