/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the Location Service
 */
@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class LocationReceiverTest {

    private LocationReceiver locationReceiver;
    private AirshipLocationManager locationManager;

    @Before
    public void setup() {
        locationManager = mock(AirshipLocationManager.class);

        locationReceiver = new LocationReceiver(new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        }, new Callable<AirshipLocationManager>() {
            @Override
            public AirshipLocationManager call() {
                return locationManager;
            }
        });
    }

    /**
     * Test location updates notifies the location manager.
     */
    @Test
    public void testLocationUpdate() {
        Location location = new Location("location");
        Bundle bundle = new Bundle();
        bundle.putParcelable(LocationManager.KEY_LOCATION_CHANGED, location);

        sendIntent(LocationReceiver.ACTION_LOCATION_UPDATE, bundle);

        verify(locationManager).onLocationUpdate(eq(location));
    }

    /**
     * Test location updates with a null location does nothing.
     */
    @Test
    public void testLocationUpdateNullLocation() {
        sendIntent(LocationReceiver.ACTION_LOCATION_UPDATE);

        // Should not call record location
        verify(locationManager, times(0)).onLocationUpdate(any(Location.class));
    }

    /**
     * Test UALocationProvider is notified of changes to the availability of system location providers.
     */
    @Test
    public void testLocationUpdateProviderChange() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(LocationManager.KEY_PROVIDER_ENABLED, true);

        sendIntent(LocationReceiver.ACTION_LOCATION_UPDATE, bundle);

        verify(locationManager).onSystemLocationProvidersChanged();
    }

    /**
     * Helper method to simulate sending an intent to the location service.
     *
     * @param action The action to send.
     */
    private void sendIntent(String action) {
        sendIntent(action, null);
    }

    /**
     * Helper method to simulate sending an intent to the location service.
     *
     * @param action The action to send.
     * @param extras Optional extras.
     */
    private void sendIntent(String action, Bundle extras) {
        Intent intent = new Intent();
        intent.setAction(action);
        if (extras != null) {
            intent.putExtras(extras);
        }
        locationReceiver.onReceive(ApplicationProvider.getApplicationContext(), intent);
    }

}
