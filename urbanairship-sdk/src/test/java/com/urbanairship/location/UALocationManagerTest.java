package com.urbanairship.location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("ResourceType")
public class UALocationManagerTest extends BaseTestCase {


    UALocationManager locationManager;
    LocationRequestOptions options;

    @Before
    public void setUp() {
        locationManager = new UALocationManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore);
        options = new LocationRequestOptions.Builder().setMinDistance(100).create();
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
}
