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
     * services, and if that fails falls back to a TLS socket factory on Jellybean - Kitkat devices.
     *
     * @param context The application context.
     * @param url The URL.
     * @return The URLConnection.
     * @throws IOException
     */
    @WorkerThread
    public static URLConnection openSecureConnection(@NonNull Context context, @NonNull URL url) throws IOException {
        boolean providerInstalled = installProvider(context);
        URLConnection connection = url.openConnection();

        // Fallback to applying the TlsSocketFactory
        if (!providerInstalled && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT && connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) connection;

            try {
                httpsURLConnection.setSSLSocketFactory(TlsSocketFactory.newFactory());
                Logger.debug("TlsSocketFactory set for HttpsURLConnection");
            } catch (Exception e) {
                Logger.error(e, "Failed to create TLS SSLSocketFactory.");
            }
        }

        return connection;
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

    /**
     * TLS 1.2 socket factory for Jellybean - Kitkat devices.
     *
     * Based on http://blog.dev-area.net/2015/08/13/android-4-1-enable-tls-1-1-and-tls-1-2/
     */
    private static class TlsSocketFactory extends SSLSocketFactory {

        private static final String[] PROTOCOLS = { "TLSv1.2" };

        private SSLSocketFactory baseFactory;

        private TlsSocketFactory(SSLSocketFactory baseFactory) {
            this.baseFactory = baseFactory;
        }

        static TlsSocketFactory newFactory() throws KeyManagementException, NoSuchAlgorithmException {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            return new TlsSocketFactory(sslContext.getSocketFactory());
        }

        public String[] getDefaultCipherSuites() {
            return baseFactory.getDefaultCipherSuites();
        }

        public String[] getSupportedCipherSuites() {
            return baseFactory.getSupportedCipherSuites();
        }

        public Socket createSocket() throws IOException {
            return onSocketCreated(baseFactory.createSocket());
        }

        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return onSocketCreated(baseFactory.createSocket(socket, host, port, autoClose));
        }

        public Socket createSocket(String host, int port) throws IOException {
            return onSocketCreated(baseFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return onSocketCreated(baseFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return onSocketCreated(baseFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return onSocketCreated(baseFactory.createSocket(address, port, localAddress, localPort));
        }

        private Socket onSocketCreated(@Nullable Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                sslSocket.setEnabledProtocols(PROTOCOLS);
            }

            return socket;
        }

    }

}
