/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.graphics.Color;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.notifications.DefaultNotificationFactory;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class PushManagerTest extends BaseTestCase {

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private Set<String> tagsToAdd = new HashSet<>();
    private Set<String> tagsToRemove = new HashSet<>();

    private PreferenceDataStore preferenceDataStore;
    private PushManager pushManager;
    private AirshipConfigOptions options;
    private Analytics mockAnalytics;
    private JobDispatcher mockDispatcher;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {
        mockDispatcher = mock(JobDispatcher.class);

        mockAnalytics = mock(Analytics.class);
        Mockito.doNothing().when(mockAnalytics).addEvent(any(Event.class));
        TestApplication.getApplication().setAnalytics(mockAnalytics);

        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;


        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, options, null, mockDispatcher);

        tagsToAdd.add("tag1");
        tagsToAdd.add("tag2");
        tagsToAdd.add("tag3");

        tagsToRemove.add("tag3");
        tagsToRemove.add("tag4");
        tagsToRemove.add("tag5");
    }

    /**
     * Test init starts push registration if the registration token is not available.
     */
    @Test
    public void testInit() {
       pushManager.init();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_PUSH_REGISTRATION);
            }
        }));

        verifyNoMoreInteractions(mockDispatcher);
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
     * Test set push token
     */
    @Test
    public void testSetPushToken() {
        String pushToken = "fakePushToken";
        pushManager.setRegistrationToken(pushToken);

        assertEquals(pushToken, pushManager.getRegistrationToken());
    }

    /**
     * Test OptIn is false when push is disabled
     */
    @Test
    public void testOptInPushDisabled() {
        pushManager.setPushEnabled(false);
        pushManager.setRegistrationToken("fakeGcmId");

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
        pushManager.setRegistrationToken("fakeAdmId");
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
        pushManager.setRegistrationToken("fakeGcmId");
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
        pushManager.setRegistrationToken(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when ADM Id is null
     */
    @Test
    public void testOptInADMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        pushManager.setPushEnabled(true);
        pushManager.setRegistrationToken(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test testGetNextChannelRegistrationPayloadAnalyticsEnabled returns a payload with a top
     * level timezone, language and country when analytics is enabled
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAnalyticsEnabled() throws JSONException {
        when(mockAnalytics.isEnabled()).thenReturn(true);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        pushManager.setRegistrationToken("GCM_TOKEN");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("timezone").getString(), TimeZone.getDefault().getID());
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_language").getString(),  Locale.getDefault().getLanguage());
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_country").getString(), Locale.getDefault().getCountry());
    }

    /**
     * Test testGetNextChannelRegistrationPayloadAnalyticsDisabled returns a payload without a top
     * level timezone, language and country when analytics is disabled
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAnalyticsDisabled() throws JSONException {
        when(mockAnalytics.isEnabled()).thenReturn(false);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        pushManager.setRegistrationToken("GCM_TOKEN");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertNull(payload.toJsonValue().getMap().get("channel").getMap().get("timezone"));
        assertNull(payload.toJsonValue().getMap().get("channel").getMap().get("locale_language"));
        assertNull(payload.toJsonValue().getMap().get("channel").getMap().get("locale_country"));
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload with android device and GCM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAndroid() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        pushManager.setRegistrationToken("GCM_TOKEN");
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
        pushManager.setRegistrationToken("ADM_ID");
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
     * Test editTagGroups apply dispatches a job to update the tag groups.
     */
    @Test
    public void testStartUpdateChannelTagService() {
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);

        pushManager.editTagGroups()
                   .addTags("tagGroup", tagsToAdd)
                   .removeTags("tagGroup", tagsToRemove)
                   .apply();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_TAG_GROUPS) &&
                        jobInfo.getAirshipComponentName().equals(PushManager.class.getName());
            }
        }));
    }

    /**
     * Test editTagGroups apply does not update the tag groups if addTags and removeTags are empty.
     */
    @Test
    public void testEmptyAddTagsRemoveTags() {
        pushManager.editTagGroups().apply();
        verifyZeroInteractions(mockDispatcher);
    }

    /**
     * Test init update channel tags.
     */
    @Test
    public void testInitUpdateChannelTags() {
        pushManager.setChannel(fakeChannelId, fakeChannelLocation);
        preferenceDataStore.put(PushManager.PUSH_ENABLED_SETTINGS_MIGRATED_KEY, true);
        pushManager.init();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_TAG_GROUPS);
            }
        }));
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
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, null);
        pushManager.init();
        assertFalse(pushManager.isChannelCreationDelayEnabled());

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, null);
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

        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, null, mockDispatcher);
        pushManager.init();

        assertTrue(pushManager.isChannelCreationDelayEnabled());

        // Re-enable channel creation to initiate channel registration
        pushManager.enableChannelCreation();

        // Ensure channel delay enabled is now false
        assertFalse(pushManager.isChannelCreationDelayEnabled());

        // Update should be called
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
            }
        }));
    }

    /**
     * Test isPushAvailable calls getGcmToken when pushTokenRegistrationEnabled is true.
     */
    @Test
    public void testPushTokenRegistrationEnabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(true);
        assertTrue(pushManager.getPushTokenRegistrationEnabled());

        pushManager.setRegistrationToken(null);
        assertFalse(pushManager.isPushAvailable());

        pushManager.setRegistrationToken("fakeGcmToken");
        assertTrue(pushManager.isPushAvailable());
    }

    /**
     * Test isPushAvailable does not call getGcmToken when pushTokenRegistrationEnabled is false.
     */
    @Test
    public void testPushTokenRegistrationDisabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(false);
        pushManager.setRegistrationToken("fakeGcmToken");

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
        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
            }
        }));

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


        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_CHANNEL_REGISTRATION);
            }
        }));
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

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, options, null);
        factory = (DefaultNotificationFactory) pushManager.getNotificationFactory();

        assertEquals(R.drawable.ua_ic_urbanairship_notification, factory.getSmallIconId());
        assertEquals(Color.parseColor("#ff0000"), factory.getColor());
    }


    /**
     * Test migrating quiet time enabled setting.
     */
    @Test
    public void testMigrateQuietTimeEnabled() {
        // Make sure only the old enable setting is set to true
        preferenceDataStore.remove(PushManager.QUIET_TIME_ENABLED);
        preferenceDataStore.put(PushManager.OLD_QUIET_TIME_ENABLED, true);

        // Verify quiet time is disabled
        assertFalse(pushManager.isQuietTimeEnabled());

        // Migrate the old setting
        pushManager.migrateQuietTimeSettings();

        // Verify quiet time is enabled
        assertTrue(pushManager.isQuietTimeEnabled());

        // Make sure changes to the setting are persisted
        pushManager.setQuietTimeEnabled(false);
        pushManager.migratePushEnabledSettings();
        assertFalse(pushManager.isQuietTimeEnabled());
    }

    /**
     * Test migrating quiet time enabled setting does not overwrite
     * the new setting if its set.
     */
    @Test
    public void testMigrateQuietTimeEnabledAlreadySet() {
        preferenceDataStore.put(PushManager.QUIET_TIME_ENABLED, false);
        preferenceDataStore.put(PushManager.OLD_QUIET_TIME_ENABLED, true);

        // Verify quiet time is disabled
        assertFalse(pushManager.isQuietTimeEnabled());

        // Migrate the old setting
        pushManager.migrateQuietTimeSettings();

        // Verify quiet time is still disabled
        assertFalse(pushManager.isQuietTimeEnabled());
    }

    /**
     * Test migrating the GCM instance ID setting to the registration token setting.
     */
    @Test
    public void testMigrateGcmRegistrationTokenSettings() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        // Initialize the GCM instance ID token
        preferenceDataStore.put(PushManager.REGISTRATION_TOKEN_MIGRATED_KEY, false);
        preferenceDataStore.put(PushManager.GCM_INSTANCE_ID_TOKEN_KEY, "gcmIdToken");

        // Verify that registration token has not been set
        assertNull(preferenceDataStore.getString(PushManager.REGISTRATION_TOKEN_KEY, null));

        // Migrate the GCM instance ID token
        pushManager.migrateRegistrationTokenSettings();

        // Verify that the token has been migrated to the new setting
        assertEquals("gcmIdToken", preferenceDataStore.getString(PushManager.REGISTRATION_TOKEN_KEY, null));
        assertTrue(preferenceDataStore.getBoolean(PushManager.REGISTRATION_TOKEN_MIGRATED_KEY, false));
    }

    /**
     * Test migrating the ADM registration ID setting to the registration token setting.
     */
    @Test
    public void testMigrateAdmRegistrationTokenSettings() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        // Initialize the ADM ID token
        preferenceDataStore.put(PushManager.REGISTRATION_TOKEN_MIGRATED_KEY, false);
        preferenceDataStore.put(PushManager.ADM_REGISTRATION_ID_KEY, "admIdToken");

        // Verify that registration token has not been set
        assertNull(preferenceDataStore.getString(PushManager.REGISTRATION_TOKEN_KEY, null));

        // Migrate the ADM ID token
        pushManager.migrateRegistrationTokenSettings();

        // Verify that the token has been migrated to the new setting
        assertEquals("admIdToken", preferenceDataStore.getString(PushManager.REGISTRATION_TOKEN_KEY, null));
        assertTrue(preferenceDataStore.getBoolean(PushManager.REGISTRATION_TOKEN_MIGRATED_KEY, false));
    }
}
