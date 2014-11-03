package com.urbanairship.analytics;

import android.location.Location;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class LocationEventTest {

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

    private PushManager mockPush;

    @Before
    public void setUp() {
        gpsLocation = createTestLocation("GPS");
        bogusProviderLocation = createTestLocation(null);
        networkProviderLocation = createTestLocation("NETWORK");
        passiveProviderLocation = createTestLocation("PASSIVE");
        unknownProviderLocation = createTestLocation("UNKNOWN");


        event = new LocationEvent(gpsLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, true);

        backgroundEvent = new LocationEvent(gpsLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, false);

        noRequestedAccuracyEvent = new LocationEvent(gpsLocation, LocationEvent.UpdateType.CONTINUOUS, -1, 1, true);
        noUpdateDistanceEvent = new LocationEvent(gpsLocation, LocationEvent.UpdateType.CONTINUOUS, 1, -1, true);

        bogusProviderEvent = new LocationEvent(bogusProviderLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, true);
        networkProviderEvent = new LocationEvent(networkProviderLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, true);
        passiveProviderEvent = new LocationEvent(passiveProviderLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, true);
        unknownProviderEvent = new LocationEvent(unknownProviderLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, true);

        singleUpdateEvent = new LocationEvent(gpsLocation, LocationEvent.UpdateType.SINGLE, 1, 1, true);
        continuousUpdateEvent = new LocationEvent(gpsLocation, LocationEvent.UpdateType.CONTINUOUS, 1, 1, false);


        mockPush = Mockito.mock(PushManager.class);
        TestApplication.getApplication().setPushManager(mockPush);
    }

    @Test
    public void testPushEnabled() throws JSONException {
        when(mockPush.isPushEnabled()).thenReturn(true);
        assertEquals(event.getEventData().get(Event.PUSH_ENABLED_KEY), true);
    }

    @Test
    public void testNotificationTypes() throws JSONException {
        JSONObject data = event.getEventData();
        JSONArray typesJSON = (JSONArray) data.get(Event.NOTIFICATION_TYPES_KEY);
        ArrayList<String> typesList = new ArrayList<String>();
        for (int i = 0; i < typesJSON.length(); i++) {
            typesList.add((String) typesJSON.get(i));
        }
        assertEquals(typesList, event.getNotificationTypes());
    }

    @Test
    public void testLatitude() throws JSONException {
        assertEquals(Double.parseDouble((String) event.getEventData().get(LocationEvent.LATITUDE_KEY)), gpsLocation.getLatitude(), 0);
    }

    @Test
    public void testLongitude() throws JSONException {
        assertEquals(Double.parseDouble((String) event.getEventData().get(LocationEvent.LONGITUDE_KEY)), gpsLocation.getLongitude(), 0);
    }

    @Test
    public void testRequestedAccuracy() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.REQUESTED_ACCURACY_KEY), "1");
        assertEquals(noRequestedAccuracyEvent.getEventData().get(LocationEvent.REQUESTED_ACCURACY_KEY), "NONE");
    }

    @Test
    public void testUpdateDistance() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.UPDATE_DISTANCE_KEY), "1");
        assertEquals(noUpdateDistanceEvent.getEventData().get(LocationEvent.UPDATE_DISTANCE_KEY), "NONE");
    }

    @Test
    public void testProvider() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.PROVIDER_KEY), gpsLocation.getProvider());
        assertEquals(networkProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY), networkProviderLocation.getProvider());
        assertEquals(passiveProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY), passiveProviderLocation.getProvider());
        assertEquals(unknownProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY), unknownProviderLocation.getProvider());
        assertEquals(bogusProviderEvent.getEventData().get(LocationEvent.PROVIDER_KEY), "UNKNOWN");
    }

    @Test
    public void testHAccuracy() throws JSONException {
        assertEquals(Float.parseFloat((String) event.getEventData().get(LocationEvent.H_ACCURACY_KEY)), gpsLocation.getAccuracy(), 0);
    }

    @Test
    public void testVAccuracy() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.V_ACCURACY_KEY), "NONE");
    }

    @Test
    public void testForeground() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.FOREGROUND_KEY), "true");
        assertEquals(backgroundEvent.getEventData().get(LocationEvent.FOREGROUND_KEY), "false");
    }

    @Test
    public void testContinousUpdateType() throws JSONException {
        assertEquals(event.getEventData().get(LocationEvent.UPDATE_TYPE_KEY), LocationEvent.UpdateType.CONTINUOUS.toString());
    }

    @Test
    public void testSingleUpdateType() throws JSONException {
        assertEquals(singleUpdateEvent.getEventData().get(LocationEvent.UPDATE_TYPE_KEY), LocationEvent.UpdateType.SINGLE.toString());
    }

    @Test
    public void testChangeUpdateType() throws JSONException {
        assertEquals(continuousUpdateEvent.getEventData().get(LocationEvent.UPDATE_TYPE_KEY), LocationEvent.UpdateType.CONTINUOUS.toString());
    }

    /**
     * Verifies the location payload uses the correct keys
     *
     * @throws JSONException
     */
    @Test
    public void testJSONKeys() throws JSONException {
        String[] expectedKeys = new String[] { "session_id", "lat", "long", "requested_accuracy", "update_type",
                                               "push_enabled", "provider", "update_dist", "h_accuracy", "v_accuracy", "foreground", "notification_types" };

        JSONObject eventData = new JSONObject(event.createEventPayload("sessionId")).getJSONObject(Event.DATA_KEY);

        for (String key : expectedKeys) {
            assertTrue(eventData.has(key));
        }
        
        assertEquals("One of these does not belong: " + eventData.names().toString(), eventData.length(), expectedKeys.length);
    }

    private Location createTestLocation(String provider) {
        Location location = new Location(provider);
        location.setLatitude(LAT);
        location.setLongitude(LON);
        return location;
    }
}
