/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.Context;
import android.location.Location;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AirshipLocationManagerTest {

    private AirshipLocationManager locationManager;
    private LocationRequestOptions options;
    private PreferenceDataStore dataStore;
    private PrivacyManager privacyManager;

    private final AirshipChannel mockChannel = mock(AirshipChannel.class);
    private final PermissionsManager mockPermissionManager = mock(PermissionsManager.class);

    @Before
    public void setUp() {
        dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext());
        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

        Context context = ApplicationProvider.getApplicationContext();
        locationManager = new AirshipLocationManager(context, dataStore, privacyManager,
                mockChannel, mockPermissionManager, GlobalActivityMonitor.shared(context));
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

    @Test
    public void testLocationUpdatesDataCollectionDisabled() {
        locationManager.setLocationRequestOptions(options);
        locationManager.setLocationUpdatesEnabled(true);
        locationManager.setBackgroundLocationAllowed(true);

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);

        Location location = new Location("provider");
        locationManager.onLocationUpdate(location);
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
        privacyManager.disable(PrivacyManager.FEATURE_LOCATION);

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
    public void testPermissionEnabler() {
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        locationManager.init();
        verify(mockPermissionManager).addAirshipEnabler(captor.capture());

        Consumer<Permission> consumer = (Consumer<Permission>) captor.getValue();
        assertNotNull(consumer);

        privacyManager.disable(PrivacyManager.FEATURE_LOCATION);
        assertFalse(locationManager.isLocationUpdatesEnabled());

        consumer.accept(Permission.LOCATION);

        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_LOCATION));
        assertTrue(locationManager.isLocationUpdatesEnabled());
    }

}
