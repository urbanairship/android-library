package com.urbanairship.accengage;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.accengage.common.persistence.AccengageSettingsLoader;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AccengageTest {

    private AirshipChannel mockChannel;
    private PushManager mockPush;
    private Analytics mockAnalytics;

    private Accengage accengage;

    @NonNull
    private JsonMap accengageSettings;

    @Before
    public void setup() {
        Application application = ApplicationProvider.getApplicationContext();
        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(application);

        mockChannel = mock(AirshipChannel.class);
        mockAnalytics = mock(Analytics.class);
        mockPush = mock(PushManager.class);

        accengageSettings = JsonMap.EMPTY_MAP;

        AccengageSettingsLoader settingsLoader = new AccengageSettingsLoader() {
            @NonNull
            @Override
            public JsonMap load(@NonNull Context context, @NonNull String filename) {
                if (Accengage.PUSH_SETTINGS_FILE.equals(filename)) {
                    return accengageSettings;
                }
                return JsonMap.EMPTY_MAP;
            }
        };

        accengage = new Accengage(application, preferenceDataStore, mockChannel, mockPush, mockAnalytics, settingsLoader);
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

        // Verify the migration does not apply twice
        verify(mockPush, times(1)).setPushEnabled(true);
        verify(mockPush, times(0)).setPushEnabled(false);
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

        // Verify the migration does not apply twice
        verify(mockPush, times(1)).setPushEnabled(false);
        verify(mockPush, times(0)).setPushEnabled(true);
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

        // Verify the migration does not apply twice
        verify(mockAnalytics, times(1)).setEnabled(false);
        verify(mockAnalytics, times(0)).setEnabled(true);
    }
  
}
