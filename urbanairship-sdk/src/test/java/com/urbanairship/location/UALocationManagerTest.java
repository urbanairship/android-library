/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.location.Location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.LocationEvent;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("ResourceType")
public class UALocationManagerTest extends BaseTestCase {


    UALocationManager locationManager;
    LocationRequestOptions options;
    Analytics analytics;


    @Before
    public void setUp() {
        analytics = mock(Analytics.class);
        locationManager = new UALocationManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, new TestActivityMonitor());
        options = new LocationRequestOptions.Builder().setMinDistance(100).create();

        TestApplication.getApplication().setAnalytics(analytics);
    }

    /**
     * Test location updates enabled preference.
     */
    @Test
    public void testLocationUpdatesEnabled() {
        assertFalse("isLocationUpdatesEnabled should default to false.", locationManager.isLocationUpdatesEnabled());

        locationManager.setLocationUpdatesEnabled(true);
        assertTrue("isLocationUpdatesEnabled should be enabled.", locationManager.isLocationUpdatesEnabled());
    }

    /**
     * Test background location allowed preference.
     */
    @Test
    public void testBackgroundLocationAllowed() {
        assertFalse("isBackgroundLocationAllowed should default to false.", locationManager.isBackgroundLocationAllowed());

        locationManager.setBackgroundLocationAllowed(true);
        assertTrue("isBackgroundLocationAllowed should be enabled.", locationManager.isBackgroundLocationAllowed());
    }

    /**
     * Test location request options preference.
     */
    @Test
    public void testLocationRequestOptions() {
        locationManager.setLocationRequestOptions(options);
        assertEquals("LocationRequestOptions not being restored properly.", options, locationManager.getLocationRequestOptions());
    }


    /**
     * Test location updates generate events.
     */
    @Test
    public void testLocationUpdates() {
        locationManager.setLocationRequestOptions(options);
        locationManager.setLocationUpdatesEnabled(true);
        locationManager.setBackgroundLocationAllowed(true);

        Location location = new Location("provider");
        locationManager.onLocationUpdate(location);

        verify(analytics).recordLocation(location, options, LocationEvent.UPDATE_TYPE_CONTINUOUS);
    }
}
