/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.location.Location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.location.LocationEvent;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("ResourceType")
public class UALocationManagerTest extends BaseTestCase {

    private AirshipLocationManager locationManager;
    private LocationRequestOptions options;
    private Analytics mockAnalytics;
    private AirshipChannel mockChannel;
    private PreferenceDataStore dataStore;

    @Before
    public void setUp() {
        mockAnalytics = mock(Analytics.class);
        mockChannel = mock(AirshipChannel.class);

        dataStore = TestApplication.getApplication().preferenceDataStore;
        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, true);

        locationManager = new AirshipLocationManager(TestApplication.getApplication(), dataStore,
                mockChannel, mockAnalytics, new TestActivityMonitor());
        options = LocationRequestOptions.newBuilder().setMinDistance(100).build();
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

        verify(mockAnalytics).recordLocation(location, options, LocationEvent.UPDATE_TYPE_CONTINUOUS);
    }

    @Test
    public void testLocationUpdatesDataCollectionDisabled() {
        locationManager.setLocationRequestOptions(options);
        locationManager.setLocationUpdatesEnabled(true);
        locationManager.setBackgroundLocationAllowed(true);

        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, false);

        Location location = new Location("provider");
        locationManager.onLocationUpdate(location);

        verify(mockAnalytics, never()).recordLocation(location, options, LocationEvent.UPDATE_TYPE_CONTINUOUS);
    }

    /**
     * Test channel registration extender adds the location settings.
     */
    @Test
    public void testChannelRegistrationPayloadExtender() {
        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        locationManager.init();
        verify(mockChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        locationManager.setLocationUpdatesEnabled(true);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();
        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setLocationSettings(true)
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test channel registration extender does not add the location settings when data opt-in is disabled.
     */
    @Test
    public void testChannelRegistrationPayloadExtenderDataCollectionDisabled() {
        dataStore.put(UAirship.DATA_COLLECTION_ENABLED_KEY, false);

        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        locationManager.init();
        verify(mockChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        locationManager.setLocationUpdatesEnabled(true);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();
        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder().build();

        assertEquals(expected, payload);
    }

    @Test
    public void testAnalyticHeaders() {
        ArgumentCaptor<Analytics.AnalyticsHeaderDelegate> captor = ArgumentCaptor.forClass(Analytics.AnalyticsHeaderDelegate.class);
        locationManager.init();
        verify(mockAnalytics).addHeaderDelegate(captor.capture());

        Analytics.AnalyticsHeaderDelegate delegate = captor.getValue();
        assertNotNull(delegate);

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("X-UA-Location-Permission", "NOT_ALLOWED");
        expectedHeaders.put("X-UA-Location-Service-Enabled", "false");

        Map<String, String> headers = delegate.onCreateAnalyticsHeaders();
        assertEquals(expectedHeaders, headers);
    }

}
