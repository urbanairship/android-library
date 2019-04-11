/* Copyright Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.TestApplication;
import com.urbanairship.TestLocaleManager;
import com.urbanairship.UAirship;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class PushManagerTest extends BaseTestCase {

    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private Set<String> tagsToAdd = new HashSet<>();
    private Set<String> tagsToRemove = new HashSet<>();

    private PreferenceDataStore preferenceDataStore;
    private PushManager pushManager;
    private AirshipConfigOptions options;
    private JobDispatcher mockDispatcher;
    private TagGroupRegistrar mockTagGroupRegistrar;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {
        mockDispatcher = mock(JobDispatcher.class);
        mockTagGroupRegistrar = mock(TagGroupRegistrar.class);

        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, options, null, mockTagGroupRegistrar, mockDispatcher);

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
     * Test on push token updated.
     */
    @Test
    public void testOnPushTokenUpdated() {
        String pushToken = "fakePushToken";
        pushManager.onPushTokenUpdated(pushToken);

        assertEquals(pushToken, pushManager.getRegistrationToken());
    }

    /**
     * Test OptIn is false when push is disabled
     */
    @Test
    public void testOptInPushDisabled() {
        pushManager.setPushEnabled(false);
        pushManager.onPushTokenUpdated("token");

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
        pushManager.onPushTokenUpdated("fakeAdmId");
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
        pushManager.onPushTokenUpdated("fakeGcmId");
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

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when ADM Id is null
     */
    @Test
    public void testOptInADMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        pushManager.setPushEnabled(true);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload with android device and GCM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAndroid() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.onChannelCreated(fakeChannelId, fakeChannelLocation);
        pushManager.onPushTokenUpdated("GCM_TOKEN");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("device_type").getString(), "android");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("push_address").getString(), "GCM_TOKEN");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_language").getString(), Locale.getDefault().getLanguage());
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_country").getString(), Locale.getDefault().getCountry());
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload with android device and GCM ID
     */
    @Test
    public void testLocale() throws JSONException {

        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.onChannelCreated(fakeChannelId, fakeChannelLocation);
        pushManager.onPushTokenUpdated("GCM_TOKEN");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("device_type").getString(), "android");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("push_address").getString(), "GCM_TOKEN");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_language").getString(), Locale.getDefault().getLanguage());
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_country").getString(), Locale.getDefault().getCountry());
    }


    /**
     * Test getNextChannelRegistrationPayload returns a payload amazon device and ADM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAmazon() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        pushManager.onChannelCreated(fakeChannelId, fakeChannelLocation);
        pushManager.onPushTokenUpdated("ADM_ID");
        pushManager.setPushTokenRegistrationEnabled(true);

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("device_type").getString(), "amazon");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("push_address").getString(), "ADM_ID");
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_language").getString(), Locale.getDefault().getLanguage());
        assertEquals(payload.toJsonValue().getMap().get("channel").getMap().get("locale_country").getString(), Locale.getDefault().getCountry());
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
        pushManager.addNotificationActionButtonGroup("ua_my_test_id", NotificationActionButtonGroup.newBuilder().build());
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
        pushManager.onChannelCreated(fakeChannelId, fakeChannelLocation);

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
        pushManager.onChannelCreated(fakeChannelId, fakeChannelLocation);
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
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, null, mockTagGroupRegistrar);
        pushManager.init();
        assertFalse(pushManager.isChannelCreationDelayEnabled());

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();
        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, null, mockTagGroupRegistrar);
        pushManager.init();
        assertTrue(pushManager.isChannelCreationDelayEnabled());

        pushManager.onChannelCreated(fakeChannelId, fakeChannelLocation);
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

        pushManager = new PushManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, null, mockTagGroupRegistrar, mockDispatcher);
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

        assertFalse(pushManager.isPushAvailable());

        pushManager.onPushTokenUpdated("fakeGcmToken");
        assertTrue(pushManager.isPushAvailable());
    }

    /**
     * Test isPushAvailable does not call getGcmToken when pushTokenRegistrationEnabled is false.
     */
    @Test
    public void testPushTokenRegistrationDisabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(false);
        pushManager.onPushTokenUpdated("fakeGcmToken");

        assertFalse(pushManager.getPushTokenRegistrationEnabled());
        assertFalse(pushManager.isPushAvailable());
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

    @Test
    public void testComponentEnabled() {
        pushManager.onComponentEnableChange(true);

        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManagerJobHandler.ACTION_UPDATE_PUSH_REGISTRATION);
            }
        }));
    }

}
