package com.urbanairship.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PushPreferencesTest extends BaseTestCase {

    private static final String KEY_PREFIX = "com.urbanairship.push";
    private static final String CHANNEL_LOCATION_KEY = KEY_PREFIX + ".CHANNEL_LOCATION";
    private static final String CHANNEL_ID_KEY = KEY_PREFIX + ".CHANNEL_ID";

    private final String SHARED_PREFERENCES_NAME = "com.urbanairship.preferences";
    private final String CHANNEL_ID_PREFERENCE_KEY = SHARED_PREFERENCES_NAME + ".CHANNEL_ID";
    private final String CHANNEL_LOCATION_PREFERENCE_KEY = SHARED_PREFERENCES_NAME + ".CHANNEL_LOCATION";
    private static final String PENDING_ADD_TAG_GROUPS_KEY = KEY_PREFIX + ".PENDING_ADD_TAG_GROUPS";
    private static final String PENDING_REMOVE_TAG_GROUPS_KEY = KEY_PREFIX + ".PENDING_REMOVE_TAG_GROUPS";

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

    /**
     * Test set pending tag groups.
     */
    @Test
    public void testPendingTagGroups() {
        Set<String> addTags = new HashSet<>();
        addTags.add("tag1");
        addTags.add("tag2");
        addTags.add("tag3");

        Map<String, Set<String>> pendingAddTags = new HashMap<>();
        pendingAddTags.put("tagGroup", addTags);

        Set<String> removeTags = new HashSet<>();
        removeTags.add("tag3");
        removeTags.add("tag4");
        removeTags.add("tag5");

        Map<String, Set<String>> pendingRemoveTags = new HashMap<>();
        pendingRemoveTags.put("tagGroup", removeTags);

        pushPref.setPendingTagGroupsChanges(pendingAddTags, pendingRemoveTags);

        assertEquals("Pending add tags should match", pendingAddTags, pushPref.getPendingAddTagGroups());
        assertEquals("Pending remove tags should match", pendingRemoveTags, pushPref.getPendingRemoveTagGroups());
    }

    /**
     * Test clear pending tag groups.
     */
    @Test
    public void testClearPendingTagGroups() {
        Map<String, Set<String>> emptyTags = new HashMap<>();

        pushPref.setPendingTagGroupsChanges(null, null);

        assertEquals("Pending add tags should be empty", emptyTags, pushPref.getPendingAddTagGroups());
        assertEquals("Pending remove tags should be empty", emptyTags, pushPref.getPendingRemoveTagGroups());
    }
}
