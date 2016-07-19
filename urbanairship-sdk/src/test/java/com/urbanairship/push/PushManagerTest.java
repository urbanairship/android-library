/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;
import android.graphics.Color;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.push.notifications.DefaultNotificationFactory;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

public class PushManagerTest extends BaseTestCase {

    Analytics mockAnalytics;
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private Set<String> tagsToAdd = new HashSet<>();
    private Set<String> tagsToRemove = new HashSet<>();

    PreferenceDataStore preferenceDataStore;
    PushManager pushManager;
    AirshipConfigOptions options;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {

        mockAnalytics = mock(Analytics.class);
        Mockito.doNothing().when(mockAnalytics).addEvent(any(Event.class));
        TestApplication.getApplication().setAnalytics(mockAnalytics);

        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;


        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, options);

        tagsToAdd.add("tag1");
        tagsToAdd.add("tag2");
        tagsToAdd.add("tag3");

        tagsToRemove.add("tag3");
        tagsToRemove.add("tag4");
        tagsToRemove.add("tag5");
    }

    /**
     * Test init only starts the push service with action START_REGISTRATION.
     */
    @Test
    public void testInit() {
       pushManager.init();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(ChannelIntentHandler.ACTION_START_REGISTRATION, startedIntent.getAction());

        // Verify we do not have any other services
        assertNull(ShadowApplication.getInstance().getNextStartedService());
    }


    /**
     * Test enabling push.
     */
    @Test
    public void testPushEnabled() {
        pushManager.setPushEnabled(true);
        assertTrue(preferenceDataStore.getBoolean(PushManager.PUSH_ENABLED_KEY, false));
    }

    /**
     * Test disabling push
     */
    @Test
    public void testPushDisabled() {
        pushManager.setPushEnabled(false);
        assertFalse(preferenceDataStore.getBoolean(PushManager.PUSH_ENABLED_KEY, true));
    }

    /**
     * Test enable QuietTime
     */
    @Test
    public void testQuietTimeEnabled() {
        pushManager.setQuietTimeEnabled(true);
        assertTrue(preferenceDataStore.getBoolean(PushManager.QUIET_TIME_ENABLED, false));
    }

    /**
     * Test disable QuietTime
     */
    @Test
    public void testQuietTimeDisabled() {
        pushManager.setQuietTimeEnabled(false);
        assertFalse(preferenceDataStore.getBoolean(PushManager.QUIET_TIME_ENABLED, true));
    }

    /**
     * Test enable sound
     */
    @Test
    public void testSoundEnabled() {
        pushManager.setSoundEnabled(true);
        assertTrue(preferenceDataStore.getBoolean(PushManager.SOUND_ENABLED_KEY, false));
    }

    /**
     * Test disable sound
     */
    @Test
    public void testSoundDisabled() {
        pushManager.setSoundEnabled(false);
        assertFalse(preferenceDataStore.getBoolean(PushManager.SOUND_ENABLED_KEY, true));
    }

    /**
     * Test enable vibrate
     */
    @Test
    public void testVibrateEnabled() {
        pushManager.setVibrateEnabled(true);
        assertTrue(preferenceDataStore.getBoolean(PushManager.VIBRATE_ENABLED_KEY, false));
    }

    /**
     * Test disable vibrate
     */
    @Test
    public void testVibrateDisabled() {
        pushManager.setVibrateEnabled(false);
        assertFalse(preferenceDataStore.getBoolean(PushManager.VIBRATE_ENABLED_KEY, true));
    }

    /**
     * Test set tags
     */
    @Test
    public void testTags() {
        HashSet<String> tags = new HashSet<>();
        tags.add("$xf*\"\"kkfj");
        tags.add("'''''7that'sit\"");
        tags.add("here's,some,comma,separated,stuff");

        pushManager.setTags(tags);
        assertEquals(tags, pushManager.getTags());
    }

    /**
     * Tests adding a null tag via setTags results in IllegalArgumentException.
     */
    @Test
    public void testSetNullTags() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tags must be non-null.");

        pushManager.setTags(null);
    }

    /**
     * Tests adding a null tag via setAliasAndTags results in IllegalArgumentException.
     */
    @Test
    public void testSetNullTagsAliasAndTags() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tags must be non-null.");

        pushManager.setTags(null);
    }

    /**
     * Tests trimming of tag's white space when tag is only white space.
     */
    @Test
    public void testSetTagsWhiteSpaceTrimmedToEmpty() {
        HashSet<String> tags = new HashSet<>();
        tags.add(" ");
        pushManager.setTags(tags);
        assertTrue(pushManager.getTags().isEmpty());
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeTagsWhiteSpaceTrimmedToValid() {
        String trimmedTag = "whitespace_test_tag";

        HashSet<String> tags = new HashSet<>();
        tags.add("    whitespace_test_tag    ");

        pushManager.setTags(tags);
        assertEquals(pushManager.getTags().iterator().next(), trimmedTag);
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeTagsOverMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");

        pushManager.setTags(tags);
        assertTrue(pushManager.getTags().isEmpty());
    }

    /**
     * Tests that max tag length of 127 chars can be set.
     */
    @Test
    public void testNormalizeTagsMaxLength() {
        String tag = "128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[";

        HashSet<String> tags = new HashSet<>();
        tags.add(tag);

        pushManager.setTags(tags);
        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeTagsZeroLength() {
        HashSet<String> tags = new HashSet<>();
        tags.add("");

        pushManager.setTags(tags);
        assertTrue(pushManager.getTags().isEmpty());

    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeTagsNullTag() {
        HashSet<String> tags = new HashSet<>();
        tags.add(null);

        pushManager.setTags(tags);
        assertTrue(pushManager.getTags().isEmpty());
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeTagsEmptySet() {
        String tag = "testTag";

        HashSet<String> tags = new HashSet<>();
        tags.add(tag);
        pushManager.setTags(tags);
        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);

        pushManager.setTags(new HashSet<String>());
        assertTrue(pushManager.getTags().isEmpty());
    }

    /**
     * Tests the removal of a bad tag from a 2 tag set.
     */
    @Test
    public void testNormalizeTagsMixedTagSet() {
        String tag = "testTag";

        HashSet<String> tags = new HashSet<>();
        tags.add(tag);
        tags.add("");

        pushManager.setTags(tags);
        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
    }

    /**
     * Tests setting alias and tags.
     */
    @Test
    public void testAliasAndTags() {
        String tag = "a_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();
        tags.add(tag);

        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests trimming of tag's white space when tag is only white space.
     */
    @Test
    public void testNormalizeAliasAndTagsWhiteSpaceTrimmedToEmpty() {
        String tag = "test_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();

        tags.add(" ");
        //add another test tag to allow updateApid call
        tags.add(tag);

        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeAliasAndTagsWhiteSpaceTrimmedToValid() {
        String tag = "whitespace_test_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();

        tags.add("    whitespace_test_tag    ");

        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsOverMaxLength() {
        String tag = "test_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");
        //add another test tag to pass tag changes check
        tags.add(tag);


        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests that max tag length of 127 chars can be set.
     */
    @Test
    public void testNormalizeAliasAndTagsMaxLength() {
        String tag = "127_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();

        tags.add(tag);

        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsZeroLength() {
        String tag = "test_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();

        tags.add("");
        //add another test tag to allow updateApid call
        tags.add(tag);


        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsNullTag() {
        String tag = "test_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();

        tags.add(null);
        //add another test tag to allow updateApid call
        tags.add(tag);


        pushManager.setAliasAndTags(alias, tags);

        assertEquals(pushManager.getTags().size(), 1);
        assertEquals(pushManager.getTags().iterator().next(), tag);
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeAliasAndTagsEmptySet() {
        String tag = " test_tag";
        String alias = "sandwich";

        HashSet<String> tags = new HashSet<>();
        tags.add(tag);
        pushManager.setTags(tags);

        pushManager.setAliasAndTags(alias, new HashSet<String>());

        assertTrue(pushManager.getTags().isEmpty());
        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Test set trimmed alias
     */
    @Test
    public void testTrimmedAlias() {
        String alias = "whitespace_test_alias";
        pushManager.setAlias("    whitespace_test_alias    ");

        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Test set alias
     */
    @Test
    public void testAlias() {
        String alias = "someAlias";
        pushManager.setAlias(alias);

        assertEquals(pushManager.getAlias(), alias);
    }

    /**
     * Tests clearing the alias by setting it to null.
     */
    @Test
    public void testClearAlias() {
        String alias = "someAlias";
        pushManager.setAlias(alias);

        pushManager.setAlias(null);
        assertTrue(pushManager.getAlias() == null);
    }

    /**
     * Test set GCM Instance ID token
     */
    @Test
    public void testSetGcmToken() {
        String gcmToken = "fakeGcmToken";
        pushManager.setGcmToken("fakeGcmToken");

        assertEquals(gcmToken, pushManager.getGcmToken());
    }

    /**
     * Test set ADM ID
     */
    @Test
    public void testSetAdmId() {
        String admId = "fakeAdmId";
        pushManager.setAdmId("fakeAdmId");

        assertEquals(admId, pushManager.getAdmId());
    }

    /**
     * Test OptIn is false when push is disabled
     */
    @Test
    public void testOptInPushDisabled() {
        pushManager.setPushEnabled(false);
        pushManager.setGcmToken("fakeGcmId");

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is true for Amazon
     */
    @Test
    public void testOptInAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        pushManager.setPushEnabled(true);
        pushManager.setUserNotificationsEnabled(true);
        pushManager.setAdmId("fakeAdmId");
        pushManager.setPushTokenRegistrationEnabled(true);

        assertEquals("OptIn should be true", true, pushManager.isOptIn());
    }

    /**
     * Test OptIn is true for Android
     */
    @Test
    public void testOptInAndroid() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushEnabled(true);
        pushManager.setUserNotificationsEnabled(true);
        pushManager.setGcmToken("fakeGcmId");
        pushManager.setPushTokenRegistrationEnabled(true);

        assertEquals("OptIn should be true", true, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when GCM Id is null
     */
    @Test
    public void testOptInGCMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushEnabled(true);
        pushManager.setGcmToken(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when ADM Id is null
     */
    @Test
    public void testOptInADMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        pushManager.setPushEnabled(true);
        pushManager.setAdmId(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload with android device and GCM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAndroid() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        pushManager.setGcmToken("GCM_TOKEN");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("device_type").getString(), "android");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("push_address").getString(), "GCM_TOKEN");
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload amazon device and ADM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAmazon() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        pushManager.setAdmId("ADM_ID");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("device_type").getString(), "amazon");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("push_address").getString(), "ADM_ID");
    }

    /**
     * Test Urban Airship notification action button groups are available
     */
    @Test
    public void testUrbanAirshipNotificationActionButtonGroups() {
        Set<String> keys = ActionButtonGroupsParser.fromXml(RuntimeEnvironment.application, R.xml.ua_notification_buttons).keySet();
        assertTrue(keys.size() > 0);

        for (String key : keys) {
            assertNotNull("Missing notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test trying to add a notification action button group with the reserved prefix
     */
    @Test
    public void testAddingNotificationActionButtonGroupWithReservedPrefix() {
        pushManager.addNotificationActionButtonGroup("ua_my_test_id", new NotificationActionButtonGroup.Builder().build());
        assertNull("Should not be able to add groups with prefix ua_", pushManager.getNotificationActionGroup("ua_my_test_id"));
    }

    /**
     * Test trying to remove a notification action button group with the reserved prefix
     */
    @Test
    public void testRemovingNotificationActionButtonGroupWithReservedPrefix() {
        Set<String> keys = ActionButtonGroupsParser.fromXml(RuntimeEnvironment.application, R.xml.ua_notification_buttons).keySet();

        for (String key : keys) {
            pushManager.removeNotificationActionButtonGroup(key);
            assertNotNull("Should not be able to remove notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test editTagGroups apply starts the update channel tag groups service.
     */
    @Test
    public void testStartUpdateChannelTagService() {

        pushManager.editTagGroups()
                   .addTags("tagGroup", tagsToAdd)
                   .removeTags("tagGroup", tagsToRemove)
                   .apply();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect Update Channel Tag Groups Service", TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS, startedIntent.getAction());
    }

    /**
     * Test adding and removing tags to device tag group pass when channelTagRegistrationEnabled is false.
     */
    @Test
    public void testChannelTagRegistrationDisabled() {

        pushManager.setChannelTagRegistrationEnabled(false);
        pushManager.editTagGroups()
                   .addTags("device", tagsToAdd)
                   .removeTags("device", tagsToRemove)
                   .apply();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect Update Channel Tag Groups Service", TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS, startedIntent.getAction());
    }

    /**
     * Test adding and removing tags to device tag group fails when channelTagRegistrationEnabled is true.
     */
    @Test
    public void testChannelTagRegistrationEnabled() {

        pushManager.setChannelTagRegistrationEnabled(true);
        pushManager.editTagGroups()
                   .addTags("device", tagsToAdd)
                   .removeTags("device", tagsToRemove)
                   .apply();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertNull("Update channel tag groups service should not have started", startedIntent);
    }

    /**
     * Test editTagGroups apply does not start the service when addTags and removeTags are empty.
     */
    @Test
    public void testEmptyAddTagsRemoveTags() {

        pushManager.editTagGroups().apply();

        Intent startedIntent = ShadowApplication.getInstance().peekNextStartedService();
        assertNull("Update channel tag groups service should not have started", startedIntent);
    }

    /**
     * Test init update channel tags.
     */
    @Test
    public void testInitUpdateChannelTags() {
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        preferenceDataStore.put(PushManager.PUSH_ENABLED_SETTINGS_MIGRATED_KEY, true);
        pushManager.init();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect start registration", ChannelIntentHandler.ACTION_START_REGISTRATION, startedIntent.getAction());

        startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect update channel tag groups service", TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS, startedIntent.getAction());
    }

    /**
     * Test delay channel creation.
     */
    @Test
    public void testDelayChannelCreation() {
        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(false)
                .build();
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options);
        pushManager.init();
        assertFalse(pushManager.isChannelCreationDelayEnabled());

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options);
        pushManager.init();
        assertTrue(pushManager.isChannelCreationDelayEnabled());

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        pushManager.init();
        assertFalse(pushManager.isChannelCreationDelayEnabled());
    }

    /**
     * Test enable channel creation.
     */
    @Test
    public void testEnableChannelCreation() {
        // Enable channel delay
        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options);
        pushManager.init();

        // Set up shadowApplication to ensure the registration update service is started after
        // channel creation re-enable
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        // Re-enable channel creation to initiate channel registration
        pushManager.enableChannelCreation();

        // Ensure channel delay enabled is now false
        assertFalse(pushManager.isChannelCreationDelayEnabled());

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect start registration", ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION, startedIntent.getAction());
    }

    /**
     * Test isPushAvailable calls getGcmToken when pushTokenRegistrationEnabled is true.
     */
    @Test
    public void testPushTokenRegistrationEnabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(true);
        assertTrue(pushManager.getPushTokenRegistrationEnabled());

        pushManager.setGcmToken(null);
        assertFalse(pushManager.isPushAvailable());

        pushManager.setGcmToken("fakeGcmToken");
        assertTrue(pushManager.isPushAvailable());
    }

    /**
     * Test isPushAvailable does not call getGcmToken when pushTokenRegistrationEnabled is false.
     */
    @Test
    public void testPushTokenRegistrationDisabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(false);
        pushManager.setGcmToken("fakeGcmToken");

        assertFalse(pushManager.getPushTokenRegistrationEnabled());
        assertFalse(pushManager.isPushAvailable());
    }

    @Test
    public void testQuietTimeIntervalMigration() {
        pushManager.init();
        assertTrue(pushManager.getQuietTimeInterval() == null);

        preferenceDataStore.put(PushManager.QuietTime.START_HOUR_KEY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1);
        preferenceDataStore.put(PushManager.QuietTime.START_MIN_KEY, 30);
        preferenceDataStore.put(PushManager.QuietTime.END_HOUR_KEY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1);
        preferenceDataStore.put(PushManager.QuietTime.END_MIN_KEY, 15);
        pushManager.init();

        int startHr = preferenceDataStore.getInt(PushManager.QuietTime.START_HOUR_KEY, PushManager.QuietTime.NOT_SET_VAL);
        int startMin = preferenceDataStore.getInt(PushManager.QuietTime.START_MIN_KEY, PushManager.QuietTime.NOT_SET_VAL);
        int endHr = preferenceDataStore.getInt(PushManager.QuietTime.END_HOUR_KEY, PushManager.QuietTime.NOT_SET_VAL);
        int endMin = preferenceDataStore.getInt(PushManager.QuietTime.END_MIN_KEY, PushManager.QuietTime.NOT_SET_VAL);

        assertTrue(startHr == -1);
        assertTrue(startMin == -1);
        assertTrue(endHr == -1);
        assertTrue(endMin == -1);

        Date[] interval = pushManager.getQuietTimeInterval();

        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY) - 1);
        start.set(Calendar.MINUTE, 30);
        start.set(Calendar.SECOND, 0);

        // Prepare the end date.
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1);
        end.set(Calendar.MINUTE, 15);
        end.set(Calendar.SECOND, 0);

        assertTrue(interval != null);
        assertTrue(interval.length == 2);

        // I hate this, but there's otherwise an inequivalence by milliseconds.
        assertEquals(start.getTime().toString(), interval[0].toString());
        assertEquals(end.getTime().toString(), interval[1].toString());

        pushManager.setQuietTimeEnabled(false);
        assertFalse(pushManager.isInQuietTime());
        pushManager.setQuietTimeEnabled(true);
        assertTrue(pushManager.isInQuietTime());
    }

    /**
     * Test edit tags.
     */
    @Test
    public void testEditTags() {
        Set<String> tags = new HashSet<>();
        tags.add("existing_tag");
        tags.add("another_existing_tag");

        // Set some existing tags first
        pushManager.setTags(tags);

        pushManager.editTags()
                   .addTag("hi")
                   .removeTag("another_existing_tag")
                   .apply();

        // Verify the new tags
        tags = pushManager.getTags();
        assertEquals(2, tags.size());
        assertTrue(tags.contains("hi"));
        assertTrue(tags.contains("existing_tag"));

        // A registration update should be triggered
        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect start registration", ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION, startedIntent.getAction());
    }


    /**
     * Test edit tags with clear set, clears the tags first before
     * doing any adds.
     */
    @Test
    public void testEditTagsClear() {
        Set<String> tags = new HashSet<>();
        tags.add("existing_tag");
        tags.add("another_existing_tag");

        // Set some existing tags first
        pushManager.setTags(tags);

        pushManager.editTags()
                   .addTag("hi")
                   .clear()
                   .apply();

        // Verify the new tags
        tags = pushManager.getTags();
        assertEquals(1, tags.size());
        assertTrue(tags.contains("hi"));

        // A registration update should be triggered
        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect start registration", ChannelIntentHandler.ACTION_UPDATE_CHANNEL_REGISTRATION, startedIntent.getAction());
    }

    /**
     * Test default notification icon and accent color.
     */
    @Test
    public void testDefaultNotificationFactory() {
        DefaultNotificationFactory factory = (DefaultNotificationFactory) pushManager.getNotificationFactory();
        assertEquals(0, factory.getSmallIconId());
        assertEquals(0, factory.getColor());

        AirshipConfigOptions options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setNotificationAccentColor(Color.parseColor("#ff0000"))
                .setNotificationIcon(R.drawable.ua_ic_urbanairship_notification)
                .build();

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, options);
        factory = (DefaultNotificationFactory) pushManager.getNotificationFactory();

        assertEquals(R.drawable.ua_ic_urbanairship_notification, factory.getSmallIconId());
        assertEquals(Color.parseColor("#ff0000"), factory.getColor());
    }
}
