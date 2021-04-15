package com.urbanairship.accengage;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.accengage.common.persistence.AccengageSettingsLoader;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.annotation.Config;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AccengageTest {

    private AirshipConfigOptions mockConfig;
    private AirshipChannel mockChannel;
    private PushManager mockPush;
    private Analytics mockAnalytics;
    private UAirship mockAirship;

    private Accengage accengage;

     @NonNull
    private JsonMap accengageSettings;
    @Before
    public void setup() {
        Application application = ApplicationProvider.getApplicationContext();
        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(application);

        mockConfig = mock(AirshipConfigOptions.class);
        mockChannel = mock(AirshipChannel.class);
        mockAnalytics = mock(Analytics.class);
        mockPush = mock(PushManager.class);
        mockAirship = mock(UAirship.class);

        accengageSettings = JsonMap.EMPTY_MAP;

        AccengageSettingsLoader settingsLoader = new AccengageSettingsLoader() {
            @NonNull
            @Override
            public JsonMap load(@NonNull Context context, @NonNull String filename) {
                return accengageSettings;
            }
        };

        accengage = new Accengage(application, mockConfig, preferenceDataStore, mockChannel, mockPush, mockAnalytics, settingsLoader);
    }

    /**
     * Test Accengage device ID adding to the Channel Registration Payload
     */
    @Test
    public void testAddAccengageDeviceId() {
        // Setup
        this.accengageSettings = JsonMap.newBuilder()
                .put(Accengage.DEVICE_ID_KEY, "accengage-device-id")
                .build();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        verify(mockChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        ChannelRegistrationPayload payload = extender.extend(new ChannelRegistrationPayload.Builder()).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setAccengageDeviceId("accengage-device-id")
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test Empty Accengage device ID
     */
    @Test
    public void testEmptyAccengageDeviceId() {
        // Setup
        this.accengageSettings = JsonMap.newBuilder()
                .build();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        verifyZeroInteractions(mockChannel);
    }

    /**
     * Test Accengage enable push setting migrates to Airship.
     */
    @Test
    public void testMigratePushEnabledSetting() {
        // Setup
        this.accengageSettings = JsonMap.newBuilder()
                .put(Accengage.IS_ENABLED_SETTING_KEY, true)
                .build();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        // Verify the migration does not apply twice
        verify(mockPush, times(1)).setUserNotificationsEnabled(true);
        verify(mockPush, times(0)).setUserNotificationsEnabled(false);
    }

    /**
     * Test Accengage disable push setting migrates to Airship.
     */
    @Test
    public void testMigratePushDisabledSetting() {
        // Setup
        this.accengageSettings = JsonMap.newBuilder()
                .put(Accengage.IS_ENABLED_SETTING_KEY, false)
                .build();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        // Verify the migration does not apply twice
        verify(mockPush, times(1)).setUserNotificationsEnabled(false);
        verify(mockPush, times(0)).setUserNotificationsEnabled(true);
    }

    /**
     * Test Accengage tracking setting enabled migrates to Airship.
     */
    @Test
    public void testMigrateTrackingEnabledSetting() {
        // Setup
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(Accengage.ACCENGAGE_PREFERENCES_FILE, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Accengage.DO_NOT_TRACK_SETTING_KEY, false);

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        // Verify the migration does not apply twice
        verify(mockAnalytics, times(1)).setEnabled(true);
        verify(mockAnalytics, times(0)).setEnabled(false);
    }

    /**
     * Test Accengage tracking setting disabled migrates to Airship.
     */
    @Test
    public void testMigrateTrackingDisabledSetting() {
        // Setup
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(Accengage.ACCENGAGE_PREFERENCES_FILE, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Accengage.DO_NOT_TRACK_SETTING_KEY, true).commit();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        // Verify the migration does not apply twice
        verify(mockAnalytics, times(1)).setEnabled(false);
        verify(mockAnalytics, times(0)).setEnabled(true);
    }

    /**
     * Test Accengage data opt-in migrates to Airship.
     */
    @Test
    public void testMigrateDataOptInSetting() {
        // Setup
        this.accengageSettings = JsonMap.newBuilder()
                                        .put(Accengage.OPTIN_DATA_KEY, Accengage.DATA_OPT_IN)
                                        .build();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        // Verify the migration does not apply twice
        verify(mockAirship, times(1)).setDataCollectionEnabled(true);
    }

    /**
     * Test Accengage data opt-out migrates to Airship.
     */
    @Test
    public void testMigrateDataOptOutSetting() {
        // Setup
        this.accengageSettings = JsonMap.newBuilder()
                                        .put(Accengage.OPTIN_DATA_KEY, Accengage.DATA_OPT_OUT)
                                        .build();

        // Test migrate
        accengage.init();
        accengage.onAirshipReady(mockAirship);

        // Verify the migration does not apply twice
        verify(mockAirship, times(1)).setDataCollectionEnabled(false);
    }


}
