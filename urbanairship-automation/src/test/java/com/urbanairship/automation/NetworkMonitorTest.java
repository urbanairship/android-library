package com.urbanairship.automation;

import android.content.Context;
import android.net.ConnectivityManager;

import com.urbanairship.util.Network;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowConnectivityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class NetworkMonitorTest {

    @Test
    public void testNetworkResumed() {
        android.net.Network mockNetwork = mock(android.net.Network.class);
        Network.ConnectionListener mockConnectionListener = mock(Network.ConnectionListener.class);

        Context context = ApplicationProvider.getApplicationContext();

        ShadowConnectivityManager shadowConnectivityManager =
                shadowOf((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));

        NetworkMonitor networkMonitor = new NetworkMonitor();
        networkMonitor.setConnectionListener(mockConnectionListener);

        Set<ConnectivityManager.NetworkCallback> networkCallbacks = shadowConnectivityManager.getNetworkCallbacks();
        assertEquals(1, networkCallbacks.size());

        List<ConnectivityManager.NetworkCallback> callbacks = new ArrayList<>(networkCallbacks);
        ConnectivityManager.NetworkCallback callback = callbacks.get(0);

        callback.onAvailable(mockNetwork);
        verify(mockConnectionListener).onConnectionChanged(true);

        callback.onLost(mockNetwork);
        verify(mockConnectionListener).onConnectionChanged(false);
    }
}
