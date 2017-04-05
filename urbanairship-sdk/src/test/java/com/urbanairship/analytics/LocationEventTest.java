/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.location.Location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocationEventTest extends BaseTestCase {

    Location gpsLocation;
    Location bogusProviderLocation;
    Location networkProviderLocation;
    Location passiveProviderLocation;
    Location unknownProviderLocation;

    //covers most cases with sensible defaults
    LocationEvent event;

    //app background status
    LocationEvent backgroundEvent;

    //special cases for requested accuracy/update distance
    LocationEvent noRequestedAccuracyEvent;
    LocationEvent noUpdateDistanceEvent;

    //alternate update types
    LocationEvent singleUpdateEvent;
    LocationEvent continuousUpdateEvent;

    //alternate providers (including bogus ones)
    LocationEvent bogusProviderEvent;
    LocationEvent networkProviderEvent;
    LocationEvent passiveProviderEvent;
    LocationEvent unknownProviderEvent;

    final double LAT = 45.5236;
    final double LON = 122.5236;

    @Before
    public void setUp() {
        gpsLocation = createTestLocation("GPS");
        bogusProviderLocation = createTestLocation(null);
        networkProviderLocation = createTestLocation("NETWORK");
        passiveProviderLocation = createTestLocation("PASSIVE");
        unknownProviderLocation = createTestLocation("UNKNOWN");


        event = new LocationEvent(gpsLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, true);

        backgroundEvent = new LocationEvent(gpsLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, false);

        noRequestedAccuracyEvent = new LocationEvent(gpsLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, -1, 1, true);
        noUpdateDistanceEvent = new LocationEvent(gpsLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, -1, true);

        bogusProviderEvent = new LocationEvent(bogusProviderLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, true);
        networkProviderEvent = new LocationEvent(networkProviderLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, true);
        passiveProviderEvent = new LocationEvent(passiveProviderLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, true);
        unknownProviderEvent = new LocationEvent(unknownProviderLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, true);

        singleUpdateEvent = new LocationEvent(gpsLocation, LocationEvent.UPDATE_TYPE_SINGLE, 1, 1, true);
        continuousUpdateEvent = new LocationEvent(gpsLocation, LocationEvent.UPDATE_TYPE_CONTINUOUS, 1, 1, false);
    }

    @Test
    public void testLatitude() throws JSONException {
        assertEquals(Double.parseDouble(event.getEventData().get(LocationEvent.LATITUDE_KEY).getString()), gpsLocation.getLatitude(), 0);
    }

    @Test
    public void testLongitude() throws JSONException {
        assertEquals(Double.parseDouble(event.getEventData().get(LocationEvent.LONGITUDE_KEY).getString()), gpsLocation.getLongitude(), 0);
    }

    @Test
    public void testRequestedAccuracy() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.REQUESTED_ACCURACY_KEY).getString(), "1");
        assertEquals(noRequestedAccuracyEvent.getEventData().get(LocationEvent.REQUESTED_ACCURACY_KEY).getString(), "NONE");
    }

    @Test
    public void testUpdateDistance() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.UPDATE_DISTANCE_KEY).getString(), "1");
        assertEquals(noUpdateDistanceEvent.getEventData().get(LocationEvent.UPDATE_DISTANCE_KEY).getString(), "NONE");
    }

    @Test
    public void testProvider() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.PROVIDER_KEY).getString(), gpsLocation.getProvider());
        assertEquals(networkProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY).getString(), networkProviderLocation.getProvider());
        assertEquals(passiveProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY).getString(), passiveProviderLocation.getProvider());
        assertEquals(unknownProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY).getString(), unknownProviderLocation.getProvider());
        assertEquals(bogusProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY).getString(), "UNKNOWN");
    }

    @Test
    public void testHAccuracy() throws JSONException {
        assertEquals(Float.parseFloat(event.getEventData().get(LocationEvent.H_ACCURACY_KEY).getString()), gpsLocation.getAccuracy(), 0);
    }

    @Test
    public void testVAccuracy() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.V_ACCURACY_KEY).getString(), "NONE");
    }

    @Test
    public void testForeground() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.FOREGROUND_KEY).getString(), "true");
        assertEquals(backgroundEvent.getEventData().get(LocationEvent.FOREGROUND_KEY).getString(), "false");
    }

    @Test
    public void testContinousUpdateType() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.UPDATE_TYPE_KEY).getString(), "CONTINUOUS");
    }

    @Test
    public void testSingleUpdateType() throws JSONException {
        assertEquals(singleUpdateEvent.getEventData().get(LocationEvent.UPDATE_TYPE_KEY).getString(), "SINGLE");
    }

    @Test
    public void testChangeUpdateType() throws JSONException {
        assertEquals(continuousUpdateEvent.getEventData().get(LocationEvent.UPDATE_TYPE_KEY).getString(), "CONTINUOUS");
    }

    /**
     * Verifies the location payload uses the correct keys
     *
     * @throws JsonException
     */
    @Test
    public void testJSONKeys() throws JsonException {
        String[] expectedKeys = new String[] { "session_id", "lat", "long", "requested_accuracy", "update_type",
                                               "provider", "update_dist", "h_accuracy", "v_accuracy", "foreground" };

        JsonMap eventData = JsonValue.parseString(event.createEventPayload("sessionId")).optMap().get(Event.DATA_KEY).optMap();

        for (String key : expectedKeys) {
            assertTrue(eventData.containsKey(key));
        }

        assertEquals("One of these does not belong: " + eventData.keySet().toString(), eventData.size(), expectedKeys.length);
    }

    private Location createTestLocation(String provider) {
        Location location = new Location(provider);
        location.setLatitude(LAT);
        location.setLongitude(LON);
        return location;
    }
}
