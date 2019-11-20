/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestLocaleManager;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AirshipChannel}.
 */
public class AirshipChannelTests extends BaseTestCase {

    private AirshipChannel airshipChannel;
    private AirshipConfigOptions options;
    private ChannelApiClient mockClient;
    private AttributeApiClient mockAttributeClient;
    private PendingAttributeMutationStore mockPendingAttributeStore;

    private TagGroupRegistrar mockTagGroupRegistrar;

    private JobDispatcher mockDispatcher;
    private TestLocaleManager testLocaleManager;
    private String timestamp = "expected_timestamp";


    private static final JobInfo UPDATE_REGISTRATION_JOB = JobInfo.newBuilder()
                                                                  .setAction("ACTION_UPDATE_CHANNEL_REGISTRATION")
                                                                  .build();

    private static final JobInfo UPDATE_ATTRIBUTES_JOB = JobInfo.newBuilder()
                                                                .setAction("ACTION_UPDATE_ATTRIBUTES")
                                                                .build();

    private static final JobInfo UPDATE_TAGS_JOB = JobInfo.newBuilder()
                                                          .setAction("ACTION_UPDATE_TAG_GROUPS")
                                                          .build();

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockTagGroupRegistrar = mock(TagGroupRegistrar.class);
        mockClient = mock(ChannelApiClient.class);
        mockAttributeClient = mock(AttributeApiClient.class);
        mockPendingAttributeStore = mock(PendingAttributeMutationStore.class);

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        testLocaleManager = new TestLocaleManager();

        airshipChannel = new AirshipChannel(getApplication(), getApplication().preferenceDataStore,
                options, mockClient, mockTagGroupRegistrar, UAirship.ANDROID_PLATFORM, testLocaleManager,
                mockDispatcher, mockPendingAttributeStore, mockAttributeClient);
    }

    /**
     * Test enabling the component updates tags and registration.
     */
    @Test
    public void testComponentEnabled() {
        airshipChannel.onComponentEnableChange(true);

        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_TAG_GROUPS");
            }
        }));

        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_TAG_GROUPS");
            }
        }));
    }

    /**
     * Test create channel.
     */
    @Test
    public void testCreateChannel() throws ChannelRequestException {
        assertNull(airshipChannel.getId());

        TestListener listener = new TestListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                super.onChannelCreated(channelId);
                assertEquals("channel", channelId);
            }
        };

        airshipChannel.addChannelListener(listener);

        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        // Kickoff the update request
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_FINISHED, result);
        assertEquals("channel", airshipChannel.getId());
        assertTrue(listener.onChannelCreatedCalled);
    }

    /**
     * Test update channel.
     */
    @Test
    public void testUpdateChannel() throws ChannelRequestException {
        testCreateChannel();
        assertNotNull(airshipChannel.getId());

        TestListener listener = new TestListener() {

            @Override
            public void onChannelUpdated(@NonNull String channelId) {
                super.onChannelUpdated(channelId);
                assertEquals("channel", channelId);
            }
        };

        airshipChannel.addChannelListener(listener);

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        verify(mockClient, times(1)).updateChannelWithPayload(eq(airshipChannel.getId()), any(ChannelRegistrationPayload.class));
        assertEquals(JobInfo.JOB_FINISHED, result);
        assertTrue(listener.onChannelUpdatedCalled);
    }

    /**
     * Test channel create retries when an exception is thrown.
     */
    @Test
    public void testChannelCreateRetriesOnException() throws ChannelRequestException {
        ChannelRequestException exception = new ChannelRequestException("error");

        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class))).thenThrow(exception);

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
        assertNull(airshipChannel.getId());
    }

    /**
     * Test channel create retries when a recoverable exception is thrown.
     */
    @Test
    public void testChannelUpdateRetriesOnException() throws ChannelRequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        ChannelRequestException exception = new ChannelRequestException("error");

        // Setup response
        doThrow(exception).when(mockClient).updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 5xx is returned.
     */
    @Test
    public void testChannelCreateRetriesOnServerError() throws ChannelRequestException {
        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 500));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
        assertNull(airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 5xx is returned.
     */
    @Test
    public void testChannelUpdateRetriesOnServerError() throws ChannelRequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 500));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 429 is returned.
     */
    @Test
    public void testChannelCreateRetriesOn429() throws ChannelRequestException {
        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 429));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
        assertNull(airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 429 is returned.
     */
    @Test
    public void testChannelUpdateRetriesOn429() throws ChannelRequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 429));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test channel update recreates the channel on update.
     */
    @Test
    public void testChannelUpdateRecreatesOn409() throws ChannelRequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        clearInvocations(mockDispatcher);

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, HttpsURLConnection.HTTP_CONFLICT));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);
        assertEquals(JobInfo.JOB_FINISHED, result);

        // Clears the channel
        assertNull(airshipChannel.getId());

        // New create should be dispatched
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL_REGISTRATION");
            }
        }));
    }

    /**
     * Test update named user tags succeeds when the registrar returns true.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() throws ChannelRequestException {
        testCreateChannel();

        when(mockTagGroupRegistrar.uploadMutations(TagGroupRegistrar.CHANNEL, "channel")).thenReturn(true);

        // Update the tags
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_TAGS_JOB);
        assertEquals(JobInfo.JOB_FINISHED, result);
    }

    /**
     * Test update tags without a channel ID fails.
     */
    @Test
    public void testUpdateTagsNoChannel() {
        // Update the tags
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_TAGS_JOB);
        assertEquals(JobInfo.JOB_FINISHED, result);

        // Verify tag group registrar was not called
        verifyZeroInteractions(mockTagGroupRegistrar);
    }

    /**
     * Test update named user retries when the upload fails.
     */
    @Test
    public void testUpdateTagsRetry() throws ChannelRequestException {
        testCreateChannel();

        when(mockTagGroupRegistrar.uploadMutations(TagGroupRegistrar.CHANNEL, "channel")).thenReturn(false);

        // Update the tags
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_TAGS_JOB);
        assertEquals(JobInfo.JOB_RETRY, result);
    }

    /**
     * Test channel registration payload
     */
    @Test
    public void testChannelRegistrationPayload() throws ChannelRequestException {
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        testLocaleManager.setDefaultLocale(new Locale("wookiee", "KASHYYYK"));

        // Add an extender
        airshipChannel.addChannelRegistrationPayloadExtender(new AirshipChannel.ChannelRegistrationPayloadExtender() {
            @NonNull
            @Override
            public ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder) {
                return builder.setUserId("cool")
                              .setPushAddress("story");
            }
        });

        airshipChannel.editTags().addTag("cool_tag").apply();

        TelephonyManager tm = (TelephonyManager) UAirship.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

        ChannelRegistrationPayload expectedPayload = new ChannelRegistrationPayload.Builder()
                .setLanguage("wookiee")
                .setCountry("KASHYYYK")
                .setDeviceType("android")
                .setTags(true, Collections.singleton("cool_tag"))
                .setTimezone(TimeZone.getDefault().getID())
                .setUserId("cool")
                .setPushAddress("story")
                .setLocationSettings(UAirship.shared().getLocationManager().isLocationUpdatesEnabled())
                .setAppVersion(UAirship.getPackageInfo().versionName)
                .setDeviceModel(Build.MODEL)
                .setApiVersion(Build.VERSION.SDK_INT)
                .setCarrier(tm.getNetworkOperatorName())
                .setSdkVersion(UAirship.getVersion())
                .build();

        // Update registration
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);
        verify(mockClient).createChannelWithPayload(expectedPayload);
    }

    /**
     * Test channel registration payload for amazon devices
     */
    @Test
    public void testAmazonChannelRegistrationPayload() throws ChannelRequestException {
        airshipChannel = new AirshipChannel(getApplication(), getApplication().preferenceDataStore,
                options, mockClient, mockTagGroupRegistrar, UAirship.AMAZON_PLATFORM, testLocaleManager,
                mockDispatcher, mockPendingAttributeStore, mockAttributeClient);

        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        TelephonyManager tm = (TelephonyManager) UAirship.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

        ChannelRegistrationPayload expectedPayload = new ChannelRegistrationPayload.Builder()
                .setDeviceType("amazon")
                .setTimezone(TimeZone.getDefault().getID())
                .setTags(true, Collections.<String>emptySet())
                .setCountry(testLocaleManager.getDefaultLocale().getCountry())
                .setLanguage(testLocaleManager.getDefaultLocale().getLanguage())
                .setLocationSettings(UAirship.shared().getLocationManager().isLocationUpdatesEnabled())
                .setAppVersion(UAirship.getPackageInfo().versionName)
                .setDeviceModel(Build.MODEL)
                .setApiVersion(Build.VERSION.SDK_INT)
                .setCarrier(tm.getNetworkOperatorName())
                .setSdkVersion(UAirship.getVersion())
                .build();

        // Update registration
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_REGISTRATION_JOB);
        verify(mockClient).createChannelWithPayload(expectedPayload);


    }

    /**
     * Test editTagGroups apply dispatches a job to update the tag groups.
     */
    @Test
    public void testTagGroupUpdates() {
        airshipChannel.editTagGroups()
                      .addTag("tagGroup", "add")
                      .removeTag("tagGroup", "remove")
                      .apply();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_TAG_GROUPS");
            }
        }));
    }

    /**
     * Test editAttribute's apply function dispatches a job to update the tag groups.
     */
    @Test
    public void testAttributesUpdates() {
        airshipChannel.editAttributes()
                      .setAttribute("expected_key", "expected_value")
                      .apply();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_ATTRIBUTES");
            }
        }));
    }

    /**
     * Test attributes update finish on 200.
     */
    @Test
    public void testAttributesUpdate200() throws ChannelRequestException {
        testCreateChannel();

        AttributeMutation expected = AttributeMutation.newSetAttributeMutation("expected_key", "expected_value");

        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(expected);

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(attributeMutations, 0);

        // Setup response to return some mutations then null
        when(mockPendingAttributeStore.peek())
                .thenReturn(expectedMutations, null);

        airshipChannel.editAttributes()
                      .setAttribute("expected_key", "expected_value")
                      .apply();

        // Setup response
        when(mockAttributeClient.updateAttributes(eq("channel"), eq(expectedMutations)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_ATTRIBUTES_JOB);

        assertEquals(JobInfo.JOB_FINISHED, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test attributes update retries when a 429 is returned.
     */
    @Test
    public void testAttributesUpdateRetriesOn429() throws ChannelRequestException {
        testCreateChannel();

        AttributeMutation expected = AttributeMutation.newSetAttributeMutation("expected_key", "expected_value");

        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(expected);

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(attributeMutations, 0);

        // Setup response
        when(mockPendingAttributeStore.peek())
                .thenReturn(expectedMutations);

        airshipChannel.editAttributes()
                      .setAttribute("expected_key", "expected_value")
                      .apply();

        // Setup response
        when(mockAttributeClient.updateAttributes(eq("channel"), eq(expectedMutations)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 429));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_ATTRIBUTES_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
    }

    /**
     * Test attributes update retries when a 5xx is returned.
     */
    @Test
    public void testAttributesUpdateRetriesOnServerError() throws ChannelRequestException {
        testCreateChannel();

        AttributeMutation expected = AttributeMutation.newSetAttributeMutation("expected_key", "expected_value");

        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(expected);

        List<PendingAttributeMutation> expectedMutations = PendingAttributeMutation.fromAttributeMutations(attributeMutations, 0);

        // Setup response
        when(mockPendingAttributeStore.peek())
                .thenReturn(expectedMutations);

        airshipChannel.editAttributes()
                      .setAttribute("expected_key", "expected_value")
                      .apply();

        // Setup response
        when(mockAttributeClient.updateAttributes(eq("channel"), eq(expectedMutations)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 500));

        // Update the registration
        int result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_ATTRIBUTES_JOB);

        assertEquals(JobInfo.JOB_RETRY, result);
    }

    /**
     * Test editTagGroups apply does not update the tag groups if addTags and removeTags are empty.
     */
    @Test
    public void testEmptyTagGroupUpdate() {
        airshipChannel.editTagGroups().apply();
        verifyZeroInteractions(mockDispatcher);
    }

    /**
     * Test empty editAttribute's apply function doesn't generate a call to update the attributes.
     */
    @Test
    public void testEmptyAttributeUpdates() {
        airshipChannel.editAttributes().apply();
        verifyZeroInteractions(mockDispatcher);
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
        airshipChannel.setTags(tags);

        airshipChannel.editTags()
                      .addTag("hi")
                      .removeTag("another_existing_tag")
                      .apply();

        // Verify the new tags
        tags = airshipChannel.getTags();
        assertEquals(2, tags.size());
        assertTrue(tags.contains("hi"));
        assertTrue(tags.contains("existing_tag"));

        // A registration update should be triggered
        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL_REGISTRATION");
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
        airshipChannel.setTags(tags);

        airshipChannel.editTags()
                      .addTag("hi")
                      .clear()
                      .apply();

        // Verify the new tags
        tags = airshipChannel.getTags();
        assertEquals(1, tags.size());
        assertTrue(tags.contains("hi"));

        verify(mockDispatcher, atLeastOnce()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL_REGISTRATION");
            }
        }));
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

        airshipChannel.setTags(tags);
        assertEquals(tags, airshipChannel.getTags());
    }


    /**
     * Tests trimming of tag's white space when tag is only white space.
     */
    @Test
    public void testSetTagsWhiteSpaceTrimmedToEmpty() {
        HashSet<String> tags = new HashSet<>();
        tags.add(" ");
        airshipChannel.setTags(tags);
        assertTrue(airshipChannel.getTags().isEmpty());
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeTagsWhiteSpaceTrimmedToValid() {
        String trimmedTag = "whitespace_test_tag";

        HashSet<String> tags = new HashSet<>();
        tags.add("    whitespace_test_tag    ");

        airshipChannel.setTags(tags);
        assertEquals(airshipChannel.getTags().iterator().next(), trimmedTag);
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeTagsOverMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");

        airshipChannel.setTags(tags);
        assertTrue(airshipChannel.getTags().isEmpty());
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

        airshipChannel.setTags(tags);
        assertEquals(airshipChannel.getTags().size(), 1);
        assertEquals(airshipChannel.getTags().iterator().next(), tag);
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeTagsZeroLength() {
        HashSet<String> tags = new HashSet<>();
        tags.add("");

        airshipChannel.setTags(tags);
        assertTrue(airshipChannel.getTags().isEmpty());

    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeTagsNullTag() {
        HashSet<String> tags = new HashSet<>();
        tags.add(null);

        airshipChannel.setTags(tags);
        assertTrue(airshipChannel.getTags().isEmpty());
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeTagsEmptySet() {
        String tag = "testTag";

        HashSet<String> tags = new HashSet<>();
        tags.add(tag);
        airshipChannel.setTags(tags);
        assertEquals(airshipChannel.getTags().size(), 1);
        assertEquals(airshipChannel.getTags().iterator().next(), tag);

        airshipChannel.setTags(new HashSet<String>());
        assertTrue(airshipChannel.getTags().isEmpty());
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

        airshipChannel.setTags(tags);
        assertEquals(airshipChannel.getTags().size(), 1);
        assertEquals(airshipChannel.getTags().iterator().next(), tag);
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

        airshipChannel = new AirshipChannel(getApplication(), getApplication().preferenceDataStore,
                options, mockClient, mockTagGroupRegistrar, UAirship.ANDROID_PLATFORM, testLocaleManager,
                mockDispatcher, mockPendingAttributeStore, mockAttributeClient);

        airshipChannel.init();
        assertFalse(airshipChannel.isChannelCreationDelayEnabled());

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();

        airshipChannel = new AirshipChannel(getApplication(), getApplication().preferenceDataStore,
                options, mockClient, mockTagGroupRegistrar, UAirship.ANDROID_PLATFORM, testLocaleManager,
                mockDispatcher, mockPendingAttributeStore, mockAttributeClient);

        airshipChannel.init();
        assertTrue(airshipChannel.isChannelCreationDelayEnabled());
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

        airshipChannel = new AirshipChannel(getApplication(), getApplication().preferenceDataStore,
                options, mockClient, mockTagGroupRegistrar, UAirship.ANDROID_PLATFORM, testLocaleManager,
                mockDispatcher, mockPendingAttributeStore, mockAttributeClient);

        airshipChannel.init();

        assertTrue(airshipChannel.isChannelCreationDelayEnabled());

        // Re-enable channel creation to initiate channel registration
        airshipChannel.enableChannelCreation();

        // Ensure channel delay enabled is now false
        assertFalse(airshipChannel.isChannelCreationDelayEnabled());

        // Update should be called
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL_REGISTRATION");
            }
        }));
    }

    private static <T> ChannelResponse<T> createResponse(T result, int status) {
        Response response = Response.newBuilder(status).setResponseBody("test").build();
        return new ChannelResponse<>(result, response);
    }

    private static class TestListener implements AirshipChannelListener {
        boolean onChannelCreatedCalled;
        boolean onChannelUpdatedCalled;

        @Override
        public void onChannelCreated(@NonNull String channelId) {
            onChannelCreatedCalled = true;
        }

        @Override
        public void onChannelUpdated(@NonNull String channelId) {
            onChannelUpdatedCalled = true;
        }
    }
}
