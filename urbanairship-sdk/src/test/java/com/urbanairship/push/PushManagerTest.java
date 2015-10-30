package com.urbanairship.push;

import android.content.Intent;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PushManagerTest extends BaseTestCase {

    Analytics mockAnalytics;
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private Set<String> tagsToAdd = new HashSet<>();
    private Set<String> tagsToRemove = new HashSet<>();

    PushPreferences mockPushPreferences;
    PushManager pushManager;
    NamedUser mockNamedUser;
    AirshipConfigOptions options;



    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {

        mockAnalytics = mock(Analytics.class);
        Mockito.doNothing().when(mockAnalytics).addEvent(any(Event.class));
        TestApplication.getApplication().setAnalytics(mockAnalytics);

        mockPushPreferences = mock(PushPreferences.class);

        mockNamedUser = mock(NamedUser.class);


        options = new AirshipConfigOptions();
        pushManager = new PushManager(TestApplication.getApplication(), mockPushPreferences, mockNamedUser, options);

        tagsToAdd.add("tag1");
        tagsToAdd.add("tag2");
        tagsToAdd.add("tag3");

        tagsToRemove.add("tag3");
        tagsToRemove.add("tag4");
        tagsToRemove.add("tag5");
    }


    /**
     * Test enabling push.
     */
    @Test
    public void testPushEnabled() {
        pushManager.setPushEnabled(true);
        verify(mockPushPreferences).setPushEnabled(true);
    }

    /**
     * Test disabling push
     */
    @Test
    public void testPushDisabled() {
        when(mockPushPreferences.isPushEnabled()).thenReturn(true);

        pushManager.setPushEnabled(false);
        verify(mockPushPreferences).setPushEnabled(false);
    }

    /**
     * Test enable QuietTime
     */
    @Test
    public void testQuietTimeEnabled() {
        pushManager.setQuietTimeEnabled(true);
        verify(mockPushPreferences).setQuietTimeEnabled(true);
    }

    /**
     * Test disable QuietTime
     */
    @Test
    public void testQuietTimeDisabled() {
        pushManager.setQuietTimeEnabled(false);
        verify(mockPushPreferences).setQuietTimeEnabled(false);
    }

    /**
     * Test enable sound
     */
    @Test
    public void testSoundEnabled() {
        pushManager.setSoundEnabled(true);
        verify(mockPushPreferences).setSoundEnabled(true);
    }

    /**
     * Test disable sound
     */
    @Test
    public void testSoundDisabled() {
        pushManager.setSoundEnabled(false);
        verify(mockPushPreferences).setSoundEnabled(false);
    }

    /**
     * Test enable vibrate
     */
    @Test
    public void testVibrateEnabled() {
        pushManager.setVibrateEnabled(true);
        verify(mockPushPreferences).setVibrateEnabled(true);
    }

    /**
     * Test disable vibrate
     */
    @Test
    public void testVibrateDisabled() {
        pushManager.setVibrateEnabled(false);
        verify(mockPushPreferences).setVibrateEnabled(false);
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
        verify(mockPushPreferences).setTags(tags);
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
        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        HashSet<String> tags = new HashSet<>();
        tags.add(" ");
        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeTagsWhiteSpaceTrimmedToValid() {
        HashSet<String> tags = new HashSet<>();

        tags.add("    whitespace_test_tag    ");

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("whitespace_test_tag");
            }
        }));
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeTagsOverMaxLength() {
        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests that max tag length of 127 chars can be set.
     */
    @Test
    public void testNormalizeTagsMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[");

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadk" +
                        "fjhskdnvpeortoivnk84389349843982ij32" +
                        "1masdflkjahsdgkfjandsgkljhasdg'k./l'" +
                        ".][;l].k,/[");
            }
        }));
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeTagsZeroLength() {
        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        HashSet<String> tags = new HashSet<>();
        tags.add("");
        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeTagsNullTag() {
        HashSet<String> tags = new HashSet<>();
        tags.add(null);

        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeTagsEmptySet() {
        HashSet<String> tags = new HashSet<>();
        tags.add("testTag");
        when(mockPushPreferences.getTags()).thenReturn(tags);

        pushManager.setTags(new HashSet<String>());
        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests the removal of a bad tag from a 2 tag set.
     */
    @Test
    public void testNormalizeTagsMixedTagSet() {
        HashSet<String> tags = new HashSet<>();
        tags.add("testTag");
        tags.add("");
        when(mockPushPreferences.getTags()).thenReturn(tags);

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("testTag");
            }
        }));
    }

    /**
     * Tests setting alias and tags.
     */
    @Test
    public void testAliasAndTags() {
        HashSet<String> tags = new HashSet<>();

        tags.add("a_tag");

        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("a_tag");
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests trimming of tag's white space when tag is only white space.
     */
    @Test
    public void testNormalizeAliasAndTagsWhiteSpaceTrimmedToEmpty() {
        HashSet<String> tags = new HashSet<>();

        tags.add(" ");
        //add another test tag to allow updateApid call
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeAliasAndTagsWhiteSpaceTrimmedToValid() {
        HashSet<String> tags = new HashSet<>();

        tags.add("    whitespace_test_tag    ");

        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("whitespace_test_tag");
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsOverMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");
        //add another test tag to pass tag changes check
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that max tag length of 127 chars can be set.
     */
    @Test
    public void testNormalizeAliasAndTagsMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[");

        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadk" +
                        "fjhskdnvpeortoivnk84389349843982ij32" +
                        "1masdflkjahsdgkfjandsgkljhasdg'k./l'" +
                        ".][;l].k,/[");
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsZeroLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("");
        //add another test tag to allow updateApid call
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsNullTag() {
        HashSet<String> tags = new HashSet<>();

        tags.add(null);
        //add another test tag to allow updateApid call
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeAliasAndTagsEmptySet() {
        HashSet<String> tags = new HashSet<>();

        tags.add("testTag");

        when(mockPushPreferences.getTags()).thenReturn(tags);

        pushManager.setAliasAndTags("sandwich", new HashSet<String>());
        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));

    }

    /**
    * Test set trimmed alias
    */
    @Test
    public void testTrimmedAlias() {
        pushManager.setAlias("    whitespace_test_alias    ");
        verify(mockPushPreferences).setAlias("whitespace_test_alias");
    }

    /**
     * Tests that we can set and get tags.
     */
    @Test
    public void testNormalizeGetTags() {
        HashSet<String> tags = new HashSet<>();

        tags.add("test_tag");
        when(mockPushPreferences.getTags()).thenReturn(tags);
        pushManager.setTags(tags);

        assertEquals("Tags should be equal", tags, pushManager.getTags());
    }

    /**
     * Tests that get tags will normalize the invalid tags.
     */
    @Test
    public void testNormalizeGetTagsWhiteSpace() {
        HashSet<String> tags = new HashSet<>();

        tags.add(" test_tag ");

        HashSet<String> normalizedTags = new HashSet<>();

        normalizedTags.add("test_tag");

        when(mockPushPreferences.getTags()).thenReturn(tags);
        assertEquals("Tags should be equal", normalizedTags, pushManager.getTags());
    }

    /**
     * Tests getTags for tags greater than MAX_TAG_LENGTH
     */
    @Test
    public void testNormalizeGetTagsLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");
        when(mockPushPreferences.getTags()).thenReturn(tags);

        assertEquals("Tags should be equal", new HashSet<String>(), pushManager.getTags());
    }

    /**
     * Test set alias
     */
    @Test
    public void testAlias() {
        pushManager.setAlias("someAlias");
        verify(mockPushPreferences).setAlias("someAlias");
    }

    /**
     * Tests clearing the alias by setting it to null.
     */
    @Test
    public void testClearAlias() {
        when(mockPushPreferences.getAlias()).thenReturn("someAliasToClear");
        pushManager.setAlias(null);
        verify(mockPushPreferences).setAlias(null);
    }

    /**
     * Test set GCM Instance ID token
     */
    @Test
    public void testSetGcmToken() {
        pushManager.setGcmToken("fakeGcmToken");
        verify(mockPushPreferences).setGcmToken("fakeGcmToken");
    }

    /**
     * Test set ADM ID
     */
    @Test
    public void testSetAdmId() {
        pushManager.setAdmId("fakeAdmId");
        verify(mockPushPreferences).setAdmId("fakeAdmId");
    }

    /**
     * Test OptIn is false when push is disabled
     */
    @Test
    public void testOptInPushDisabled() {
        when(mockPushPreferences.isPushEnabled()).thenReturn(false);
        when(mockPushPreferences.getGcmToken()).thenReturn("fakeGcmId");

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is true for Amazon
     */
    @Test
    public void testOptInAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);
        when(mockPushPreferences.getAdmId()).thenReturn("fakeAdmId");
        when(mockPushPreferences.getPushTokenRegistrationEnabled()).thenReturn(true);

        assertEquals("OptIn should be true", true, pushManager.isOptIn());
    }

    /**
     * Test OptIn is true for Android
     */
    @Test
    public void testOptInAndroid() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);
        when(mockPushPreferences.getPushTokenRegistrationEnabled()).thenReturn(true);
        when(mockPushPreferences.getGcmToken()).thenReturn("fakeGcmId");

        assertEquals("OptIn should be true", true, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when GCM Id is null
     */
    @Test
    public void testOptInGCMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getGcmToken()).thenReturn(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when ADM Id is null
     */
    @Test
    public void testOptInADMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getAdmId()).thenReturn(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload with android device and GCM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAndroid() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        when(mockPushPreferences.getChannelId()).thenReturn(fakeChannelId);
        when(mockPushPreferences.getChannelLocation()).thenReturn(fakeChannelLocation);
        when(mockPushPreferences.getGcmToken()).thenReturn("GCM_TOKEN");
        when(mockPushPreferences.getPushTokenRegistrationEnabled()).thenReturn(true);

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

        when(mockPushPreferences.getChannelId()).thenReturn(fakeChannelId);
        when(mockPushPreferences.getChannelLocation()).thenReturn(fakeChannelLocation);
        when(mockPushPreferences.getAdmId()).thenReturn("ADM_ID");
        when(mockPushPreferences.getPushTokenRegistrationEnabled()).thenReturn(true);

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
        Set<String> keys = NotificationActionButtonGroupFactory.createUrbanAirshipGroups().keySet();
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
        Set<String> keys = NotificationActionButtonGroupFactory.createUrbanAirshipGroups().keySet();

        for (String key : keys) {
            pushManager.removeNotificationActionButtonGroup(key);
            assertNotNull("Should not be able to remove notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test init starts named user and tags update service.
     */
    @Test
    public void testInitStartNamedUserUpdateService() {
        when(mockNamedUser.getId()).thenReturn("named user");

        pushManager.init();
        verify(mockNamedUser).startUpdateService();
        verify(mockNamedUser).startUpdateTagsService();
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
        assertEquals("Expect Update Channel Tag Groups Service", PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS, startedIntent.getAction());
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
        assertEquals("Expect Update Channel Tag Groups Service", PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS, startedIntent.getAction());
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

        when(mockPushPreferences.getChannelId()).thenReturn(fakeChannelId);
        pushManager.init();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect start registration", PushService.ACTION_START_REGISTRATION, startedIntent.getAction());

        startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals("Expect update channel tag groups service", PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS, startedIntent.getAction());
    }

    /**
     * Test delay channel creation.
     */
    @Test
    public void testDelayChannelCreation() {
        options.channelCreationDelayEnabled = false;
        pushManager.init();
        assertFalse(pushManager.isChannelCreationDelayEnabled());

        options.channelCreationDelayEnabled = true;
        pushManager.init();
        assertTrue(pushManager.isChannelCreationDelayEnabled());

        when(mockPushPreferences.getChannelId()).thenReturn(fakeChannelId);
        pushManager.init();
        assertFalse(pushManager.isChannelCreationDelayEnabled());
    }

    /**
     * Test enable channel creation.
     */
    @Test
    public void testEnableChannelCreation() {
        // Enable channel delay
        options.channelCreationDelayEnabled = true;
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
        assertEquals("Expect start registration", PushService.ACTION_UPDATE_CHANNEL_REGISTRATION, startedIntent.getAction());
    }

    /**
     * Test isPushAvailable calls getGcmToken when pushTokenRegistrationEnabled is true.
     */
    @Test
    public void testPushTokenRegistrationEnabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(true);
        verify(mockPushPreferences).setPushTokenRegistrationEnabled(true);

        when(mockPushPreferences.getGcmToken()).thenReturn("fakeGcmToken");
        when(mockPushPreferences.getPushTokenRegistrationEnabled()).thenReturn(true);

        assertTrue(pushManager.isPushAvailable());
        verify(mockPushPreferences).getPushTokenRegistrationEnabled();
    }

    /**
     * Test isPushAvailable does not call getGcmToken when pushTokenRegistrationEnabled is false.
     */
    @Test
    public void testPushTokenRegistrationDisabled() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        pushManager.setPushTokenRegistrationEnabled(false);
        verify(mockPushPreferences).setPushTokenRegistrationEnabled(false);

        when(mockPushPreferences.getGcmToken()).thenReturn("fakeGcmToken");
        when(mockPushPreferences.getPushTokenRegistrationEnabled()).thenReturn(false);

        assertFalse(pushManager.isPushAvailable());
        verify(mockPushPreferences).getPushTokenRegistrationEnabled();
    }
}
