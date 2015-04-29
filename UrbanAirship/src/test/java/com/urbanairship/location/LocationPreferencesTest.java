package com.urbanairship.location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocationPreferencesTest extends BaseTestCase {

    LocationPreferences preferences;
    LocationRequestOptions options;

    @Before
    public void setUp() {
        preferences = new LocationPreferences(TestApplication.getApplication().preferenceDataStore);
        options = new LocationRequestOptions.Builder().setMinDistance(100).create();
    }

    /**
     * Test location updates enabled preference.
     */
    @Test
    public void testLocationUpdatesEnabled() {
        assertFalse("isLocationUpdatesEnabled should default to false.", preferences.isLocationUpdatesEnabled());

        preferences.setLocationUpdatesEnabled(true);
        assertTrue("isLocationUpdatesEnabled should be enabled.", preferences.isLocationUpdatesEnabled());
    }

    /**
     * Test background location allowed preference.
     */
    @Test
    public void testBackgroundLocationAllowed() {
        assertFalse("isBackgroundLocationAllowed should default to false.", preferences.isBackgroundLocationAllowed());

        preferences.setBackgroundLocationAllowed(true);
        assertTrue("isBackgroundLocationAllowed should be enabled.", preferences.isBackgroundLocationAllowed());
    }

    /**
     * Test location request options preference.
     */
    @Test
    public void testLocationRequestOptions() {
        assertNull("LocationRequestOptions should default to null.", preferences.getLocationRequestOptions());

        preferences.setLocationRequestOptions(options);
        assertEquals("LocationRequestOptions not being restored properly.", options, preferences.getLocationRequestOptions());
    }

    /**
     * Test change listener notifies unique preference changes.
     */
    @Test
    public void testChangeListener() {
        final List<String> changedKeys = new ArrayList<>();
        preferences.setListener(new PreferenceDataStore.PreferenceChangeListener() {
            @Override
            public void onPreferenceChange(String key) {
                changedKeys.add(key);
            }
        });

        preferences.setLocationRequestOptions(options);

        assertEquals(1, changedKeys.size());
        assertEquals(changedKeys.get(0), LocationPreferences.LOCATION_OPTIONS);

        preferences.setLocationRequestOptions(options);

        // Verify setting same value does not notify change
        assertEquals(1, changedKeys.size());
        assertEquals(changedKeys.get(0), LocationPreferences.LOCATION_OPTIONS);


        // Change the other preferences
        preferences.setLocationUpdatesEnabled(true);
        preferences.setBackgroundLocationAllowed(true);

        // Verify listener is notified
        assertEquals(3, changedKeys.size());
        assertEquals(changedKeys.get(1), LocationPreferences.LOCATION_UPDATES_ENABLED);
        assertEquals(changedKeys.get(2), LocationPreferences.BACKGROUND_UPDATES_ALLOWED);
    }
}
