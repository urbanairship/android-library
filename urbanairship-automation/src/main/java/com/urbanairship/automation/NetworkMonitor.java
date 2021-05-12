package com.urbanairship.automation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import com.urbanairship.UAirship;
import com.urbanairship.util.Network.ConnectionListener;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkMonitor {

    private ConnectionListener connectionListener;

    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            if (connectionListener != null) {
                connectionListener.onConnectionChanged(true);
            }
        }

        public void onLost(Network network) {
            if (connectionListener != null) {
                connectionListener.onConnectionChanged(false);
            }
        }
    };

    public void registerNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        }
    }

    public void unregisterNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        connectivityManager.unregisterNetworkCallback(networkCallback);
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
