package com.urbanairship.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricGradleTestRunner.class)
public class PushPreferencesTest {

    private static final String KEY_PREFIX = "com.urbanairship.push";
    private static final String CHANNEL_LOCATION_KEY = KEY_PREFIX + ".CHANNEL_LOCATION";
    private static final String CHANNEL_ID_KEY = KEY_PREFIX + ".CHANNEL_ID";

    private final String SHARED_PREFERENCES_NAME = "com.urbanairship.preferences";
    private final String CHANNEL_ID_PREFERENCE_KEY = SHARED_PREFERENCES_NAME + ".CHANNEL_ID";
    private final String CHANNEL_LOCATION_PREFERENCE_KEY = SHARED_PREFERENCES_NAME + ".CHANNEL_LOCATION";

    private PushPreferences pushPref;
    Context context = UAirship.getApplicationContext();
    SharedPreferences sharedPref = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);

    private final String fakeApid = "BBBBBBBB-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String APID_KEY = "com.urbanairship.push.APID";

    @Before
    public void setup() {
        pushPref = new PushPreferences(TestApplication.getApplication().preferenceDataStore);
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

    /**
     * Test setLastRegistrationPayload
     */
    @Test
    public void testSetLastRegistrationPayload() {
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        pushPref.setLastRegistrationPayload(payload);
        assertEquals("The payloads should match", payload, pushPref.getLastRegistrationPayload());
    }

    /**
     * Test setLastRegistrationPayload with null payload
     */
    @Test
    public void testSetLastRegistrationNullPayload() {
        pushPref.setLastRegistrationPayload(null);
        assertNull("The payload should be null", pushPref.getLastRegistrationPayload());
    }

    /**
     * Test setLastRegistrationTime
     */
    @Test
    public void testSetLastRegistrationTime() {
        assertEquals("Last registration time should default to 0", 0L, pushPref.getLastRegistrationTime());

        pushPref.setLastRegistrationTime(100L);
        assertEquals(100L, pushPref.getLastRegistrationTime());

        // Set it to the future
        pushPref.setLastRegistrationTime(System.currentTimeMillis() + 1000000);
        assertEquals("Last registration time should default to 0 if its in the future",
                0L, pushPref.getLastRegistrationTime());
    }
}
