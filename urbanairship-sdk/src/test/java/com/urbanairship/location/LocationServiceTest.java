/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the Location Service
 */
public class LocationServiceTest extends BaseTestCase {

    private LocationService locationService;
    private UALocationManager locationManager;

    @Before
    public void setup() {
        locationManager = mock(UALocationManager.class);
        TestApplication.getApplication().setLocationManager(locationManager);

        locationService = new LocationService();
    }

    /**
     * Test location updates notifies the location manager.
     */
    @Test
    public void testLocationUpdate() {
        Location location = new Location("location");
        Bundle bundle = new Bundle();
        bundle.putParcelable(LocationManager.KEY_LOCATION_CHANGED, location);

        sendIntent(LocationService.ACTION_LOCATION_UPDATE, bundle);

        verify(locationManager).onLocationUpdate(eq(location));
    }

    /**
     * Test location updates with a null location does nothing.
     */
    @Test
    public void testLocationUpdateNullLocation() {
        sendIntent(LocationService.ACTION_LOCATION_UPDATE);

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

        sendIntent(LocationService.ACTION_LOCATION_UPDATE, bundle);

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
        locationService.onHandleIntent(intent);
    }

}
