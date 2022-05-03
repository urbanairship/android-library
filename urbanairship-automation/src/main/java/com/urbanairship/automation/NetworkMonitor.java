package com.urbanairship.automation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkMonitor {

    public interface ConnectionListener {

        /**
         * Called when the connection state changed.
         * @param isConnected
         */
        void onConnectionChanged(boolean isConnected);
    }

    private ConnectionListener connectionListener;

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            if (connectionListener != null) {
                connectionListener.onConnectionChanged(true);
            }
        }

        public void onLost(@NonNull Network network) {
            if (connectionListener != null) {
                connectionListener.onConnectionChanged(false);
            }
        }
    };

    public void registerNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } catch (SecurityException e) {
            // Workaround "Package android does not belong to 10246" exceptions that occur on
            // a small set of devices.
            Logger.warn(e, "NetworkMonitor failed to register network callback!");
        }
    }

    public void unregisterNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            // Ignore unregister errors in case the callback wasn't previously registered.
            Logger.warn(e, "NetworkMonitor failed to unregister network callback!");
        }
    }

    public void setConnectionListener(ConnectionListener listener) {
        connectionListener = listener;
        registerNetworkCallback();
    }

    public void teardown() {
        connectionListener = null;
        unregisterNetworkCallback();
    }
}
