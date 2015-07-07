package com.urbanairship.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PushPreferencesTest extends BaseTestCase {

    private final String SHARED_PREFERENCES_NAME = "com.urbanairship.preferences";
    private final String CHANNEL_ID_PREFERENCE_KEY = SHARED_PREFERENCES_NAME + ".CHANNEL_ID";
    private final String CHANNEL_LOCATION_PREFERENCE_KEY = SHARED_PREFERENCES_NAME + ".CHANNEL_LOCATION";


    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";

    private PushPreferences pushPref;
    private SharedPreferences sharedPref;

    @Before
    public void setup() {
        pushPref = new PushPreferences(TestApplication.getApplication().preferenceDataStore);

        Context context = UAirship.getApplicationContext();
        sharedPref = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Test get channel will sync preferences with shared preferences
     */
    @Test
    public void testGetChannelFromSharedPref() {
        // make sure channel preferences are null
        assertNull("Channel ID should not exist", pushPref.getChannelId());
        assertNull("Channel location should not exist", pushPref.getChannelLocation());

        // set channel shared preferences
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(CHANNEL_LOCATION_PREFERENCE_KEY, fakeChannelLocation).commit();
        editor.putString(CHANNEL_ID_PREFERENCE_KEY, fakeChannelId).commit();

        // sync preferences with shared preferences
        pushPref.getChannelId();
        pushPref.getChannelLocation();

        // make sure channels in preferences are in sync with shared preferences
        assertEquals("Channel ID should match", pushPref.getChannelId(), sharedPref.getString(CHANNEL_ID_PREFERENCE_KEY, null));
        assertEquals("Channel location should match", pushPref.getChannelLocation(), sharedPref.getString(CHANNEL_LOCATION_PREFERENCE_KEY, null));
    }
}
