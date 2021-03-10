package com.urbanairship.automation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import com.urbanairship.util.Network;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class NetworkMonitorTest {

    private Context context;

    @Test
    public void testNetworkResumed() {

        context = ApplicationProvider.getApplicationContext();

        Network.ConnectionListener connectionListener = mock(Network.ConnectionListener.class);
        NetworkMonitor networkMonitor = new NetworkMonitor();

        networkMonitor.setConnectionListener(connectionListener);

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkCapabilities networkCapabilities = ShadowNetworkCapabilities.newInstance();
        shadowOf(networkCapabilities).removeTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        NetworkInfo networkInfo =  ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.DISCONNECTED, ConnectivityManager.TYPE_WIFI, 0, false, false);
        shadowOf(connectivityManager).setActiveNetworkInfo(networkInfo);
        //shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.getActiveNetwork(), networkCapabilities);

        assertFalse(Network.isConnected());

        NetworkInfo connectedNetworkInfo =  ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true);
        shadowOf(connectivityManager).setActiveNetworkInfo(connectedNetworkInfo);

        assertTrue(Network.isConnected());
        verify(connectionListener).onConnectionChanged(false);

    }

}
