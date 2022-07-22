/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import static android.os.Looper.getMainLooper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ResultCallback;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestClock;
import com.urbanairship.UAirship;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonValue;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.CachedValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

/**
 * Tests for {@link AirshipChannel}.
 */
public class AirshipChannelTests extends BaseTestCase {

    private AirshipChannel airshipChannel;
    private ChannelApiClient mockClient;
    private AttributeRegistrar mockAttributeRegistrar;
    private TagGroupRegistrar mockTagGroupRegistrar;
    private SubscriptionListRegistrar mockSubscriptionListRegistrar;

    private JobDispatcher mockDispatcher;
    private LocaleManager localeManager;

    private PreferenceDataStore dataStore;

    private TestAirshipRuntimeConfig runtimeConfig;
    private final TestClock clock = new TestClock();
    private PrivacyManager privacyManager;
    private final CachedValue<Set<String>> subscriptionListCache = new CachedValue<>(clock);
    private final TestActivityMonitor testActivityMonitor = new TestActivityMonitor();

    private static final JobInfo UPDATE_CHANNEL_JOB = JobInfo.newBuilder()
                                                             .setAction("ACTION_UPDATE_CHANNEL")
                                                             .build();

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockClient = mock(ChannelApiClient.class);
        mockAttributeRegistrar = mock(AttributeRegistrar.class);
        mockTagGroupRegistrar = mock(TagGroupRegistrar.class);
        mockSubscriptionListRegistrar = mock(SubscriptionListRegistrar.class);
        dataStore = getApplication().preferenceDataStore;
        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        localeManager = new LocaleManager(getApplication(), dataStore);

        airshipChannel = new AirshipChannel(getApplication(), dataStore,
                runtimeConfig, privacyManager, localeManager, mockDispatcher, clock,
                mockClient, mockAttributeRegistrar, mockTagGroupRegistrar, mockSubscriptionListRegistrar,
                subscriptionListCache, testActivityMonitor);
    }

    @Test
    public void testInitSetsIdOnRegistrars() {
        dataStore.put("com.urbanairship.push.CHANNEL_ID", "channel");

        clearInvocations(mockTagGroupRegistrar);
        clearInvocations(mockAttributeRegistrar);
        clearInvocations(mockSubscriptionListRegistrar);

        airshipChannel.init();
        verify(mockTagGroupRegistrar).setId("channel", false);
        verify(mockAttributeRegistrar).setId("channel", false);
        verify(mockSubscriptionListRegistrar).setId("channel", false);
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
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    /**
     * Test create channel.
     */
    @Test
    public void testCreateChannel() throws RequestException {
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

        // Setup Attribute, Tag Groups, and Subscription Lists results
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        // Kickoff the update request
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.SUCCESS, result);
        assertEquals("channel", airshipChannel.getId());
        assertTrue(listener.onChannelCreatedCalled);

        verify(mockAttributeRegistrar).setId("channel", false);
        verify(mockTagGroupRegistrar).setId("channel", false);
        verify(mockSubscriptionListRegistrar).setId("channel", false);

        // Verify the channel created intent was fired
        List<Intent> intents = shadowOf(RuntimeEnvironment.application).getBroadcastIntents();
        assertEquals(intents.size(), 0);
    }

    /**
     * Test create channel. Also tests the CHANNEL_CREATED broadcast.
     */
    @Test
    public void testCreateChannelWithExtendedBroadcasts() throws RequestException {
        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setAppKey("appKey")
                .setAppSecret("appSecret")
                .setExtendedBroadcastsEnabled(true)
                .build();
        runtimeConfig.setConfigOptions(configOptions);

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

        // Setup Attribute, Tag Groups, and Subscription Lists results
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        // Kickoff the update request
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.SUCCESS, result);
        assertEquals("channel", airshipChannel.getId());
        assertTrue(listener.onChannelCreatedCalled);

        // Verify the airship ready intent was fired
        List<Intent> intents = shadowOf(RuntimeEnvironment.application).getBroadcastIntents();
        assertEquals(intents.size(), 1);
        assertEquals(intents.get(0).getAction(), AirshipChannel.ACTION_CHANNEL_CREATED);
        assertNotNull(intents.get(0).getExtras());
        assertEquals(intents.get(0).getExtras().getString("channel_id"), "channel");
    }

    /**
     * Test update channel.
     */
    @Test
    public void testUpdateChannel() throws RequestException {
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

        // Setup Attribute, Tag Groups, and Subscription Lists results
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        verify(mockClient, times(1)).updateChannelWithPayload(eq(airshipChannel.getId()), any(ChannelRegistrationPayload.class));
        // Should be called 2 times, one after onCreateChannel, the other after onUpdateChannel
        verify(mockAttributeRegistrar, times(2)).uploadPendingMutations();
        verify(mockTagGroupRegistrar, times(2)).uploadPendingMutations();
        verify(mockSubscriptionListRegistrar, times(2)).uploadPendingMutations();
        assertEquals(JobResult.SUCCESS, result);
        assertTrue(listener.onChannelUpdatedCalled);
    }

    /**
     * Test channel create retries when an exception is thrown.
     */
    @Test
    public void testChannelCreateRetriesOnException() throws RequestException {
        RequestException exception = new RequestException("error");

        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class))).thenThrow(exception);

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.RETRY, result);
        assertNull(airshipChannel.getId());
    }

    /**
     * Test channel create retries when a recoverable exception is thrown.
     */
    @Test
    public void testChannelUpdateRetriesOnException() throws RequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        RequestException exception = new RequestException("error");

        // Setup response
        doThrow(exception).when(mockClient).updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class));

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.RETRY, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 5xx is returned.
     */
    @Test
    public void testChannelCreateRetriesOnServerError() throws RequestException {
        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 500));

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.RETRY, result);
        assertNull(airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 5xx is returned.
     */
    @Test
    public void testChannelUpdateRetriesOnServerError() throws RequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 500));

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.RETRY, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 429 is returned.
     */
    @Test
    public void testChannelCreateRetriesOn429() throws RequestException {
        // Setup response
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 429));

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.RETRY, result);
        assertNull(airshipChannel.getId());
    }

    /**
     * Test channel create retries when a 429 is returned.
     */
    @Test
    public void testChannelUpdateRetriesOn429() throws RequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 429));

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        assertEquals(JobResult.RETRY, result);
        assertEquals("channel", airshipChannel.getId());
    }

    /**
     * Test channel update recreates the channel on update.
     */
    @Test
    public void testChannelUpdateRecreatesOn409() throws RequestException {
        testCreateChannel();

        TestListener listener = new TestListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                super.onChannelCreated(channelId);
                assertEquals("channel", channelId);
            }
        };

        airshipChannel.addChannelListener(listener);

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        clearInvocations(mockDispatcher);

        // Setup response
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, HttpsURLConnection.HTTP_CONFLICT));

        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        // Setup Attribute, Tag Groups, and Subscription Lists results
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        // Update the registration
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        assertEquals(JobResult.SUCCESS, result);

        // New channel should have been created
        assertEquals("channel", airshipChannel.getId());
        assertTrue(listener.onChannelCreatedCalled);
    }

    /**
     * Test update tag groups.
     */
    @Test
    public void testUpdateTagsSucceed() throws RequestException {
        testCreateChannel();

        // Setup responses
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        // Update the tags
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        assertEquals(JobResult.SUCCESS, result);
    }

    /**
     * Test update named user retries when the upload fails.
     */
    @Test
    public void testUpdateTagsRetry() throws RequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        clearInvocations(mockDispatcher);

        // Setup responses
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(false);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        // Update the tags
        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        assertEquals(JobResult.RETRY, result);
    }

    /**
     * Test update attributes.
     */
    @Test
    public void testUpdateAttributesSucceed() throws RequestException {
        testCreateChannel();

        // Setup responses
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        assertEquals(JobResult.SUCCESS, result);
    }

    /**
     * Test update named user retries when the upload fails.
     */
    @Test
    public void testUpdateAttributesRetry() throws RequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editTags().addTag("cool").apply();

        clearInvocations(mockDispatcher);

        // Setup responses
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(false);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(true);

        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        assertEquals(JobResult.RETRY, result);
    }

    /**
     * Test update subscription lists retries when the upload fails.
     */
    @Test
    public void testUpdateSubscriptionListsRetry() throws RequestException {
        testCreateChannel();

        // Modify the payload so it actually updates the registration
        airshipChannel.editSubscriptionLists()
                      .subscribe("foo")
                      .unsubscribe("bar")
                      .apply();

        clearInvocations(mockDispatcher);

        // Setup responses
        when(mockClient.updateChannelWithPayload(eq("channel"), any(ChannelRegistrationPayload.class)))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));
        when(mockAttributeRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockTagGroupRegistrar.uploadPendingMutations()).thenReturn(true);
        when(mockSubscriptionListRegistrar.uploadPendingMutations()).thenReturn(false);

        JobResult result = airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        assertEquals(JobResult.RETRY, result);
    }

    /**
     * Test channel registration payload
     */
    @Test
    public void testChannelRegistrationPayload() throws RequestException {
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        localeManager.setLocaleOverride(new Locale("wookiee", "KASHYYYK"));

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
                .setAppVersion(UAirship.getPackageInfo().versionName)
                .setDeviceModel(Build.MODEL)
                .setApiVersion(Build.VERSION.SDK_INT)
                .setCarrier(tm.getNetworkOperatorName())
                .setSdkVersion(UAirship.getVersion())
                .setIsActive(false)
                .build();

        // Update registration
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        verify(mockClient).createChannelWithPayload(expectedPayload);
    }

    /**
     * Test channel registration payload when in foreground.
     */
    @Test
    public void testChannelRegistrationPayloadActive() throws RequestException {
        testActivityMonitor.foreground();

        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        // Update registration
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        verify(mockClient).createChannelWithPayload(ArgumentMatchers.argThat(argument -> argument.isActive));
    }

    @Test
    public void testForegroundDispatchesUpdate()  {
        airshipChannel.init();

        testActivityMonitor.foreground();

        verify(mockDispatcher, times(1))
                .dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP));
    }

    /**
     * Test channel registration payload when attributes and tags are disabled.
     */
    @Test
    public void testChannelRegistrationPayloadDataCollectionDisabled() throws RequestException {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES);
        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);

        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        localeManager.setLocaleOverride(new Locale("wookiee", "KASHYYYK"));

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

        ChannelRegistrationPayload expectedPayload = new ChannelRegistrationPayload.Builder()
                .setLanguage("wookiee")
                .setCountry("KASHYYYK")
                .setDeviceType("android")
                .setTags(true, Collections.<String>emptySet())
                .setTimezone(TimeZone.getDefault().getID())
                .setSdkVersion(UAirship.getVersion())
                .setUserId("cool")
                .setPushAddress("story")
                .build();

        // Update registration
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        verify(mockClient).createChannelWithPayload(expectedPayload);
    }

    /**
     * Test channel registration payload for amazon devices
     */
    @Test
    public void testAmazonChannelRegistrationPayload() throws RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);

        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));

        TelephonyManager tm = (TelephonyManager) UAirship.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

        ChannelRegistrationPayload expectedPayload = new ChannelRegistrationPayload.Builder()
                .setDeviceType("amazon")
                .setTimezone(TimeZone.getDefault().getID())
                .setTags(true, Collections.<String>emptySet())
                .setCountry(localeManager.getLocale().getCountry())
                .setLanguage(localeManager.getLocale().getLanguage())
                .setAppVersion(UAirship.getPackageInfo().versionName)
                .setDeviceModel(Build.MODEL)
                .setApiVersion(Build.VERSION.SDK_INT)
                .setCarrier(tm.getNetworkOperatorName())
                .setSdkVersion(UAirship.getVersion())
                .build();

        // Update registration
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        verify(mockClient).createChannelWithPayload(expectedPayload);
    }

    /**
     * Test editTagGroups apply does not dispatch a job to update the tag groups when data collection is disabled.
     */
    @Test
    public void testTagGroupUpdatesDataCollectionDisabled() {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES);

        airshipChannel.editTagGroups()
                      .addTag("tagGroup", "add")
                      .removeTag("tagGroup", "remove")
                      .apply();

        verify(mockDispatcher, times(0)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    /**
     * Test editTagGroups apply does not dispatch a job to update the tag groups when data collection is disabled.
     */
    @Test
    public void testAttributesUpdatesDataCollectionDisabled() {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES);

        airshipChannel.editAttributes()
                      .setAttribute("expected_key", "expected_value")
                      .apply();

        verify(mockDispatcher, times(0)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    /**
     * Test editSubscriptionLists does not dispatch a job to update subscriptions when data collection is disabled.
     */
    @Test
    public void testSubscriptionListUpdatesDataCollectionDisabled() {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES);

        airshipChannel.editSubscriptionLists()
                      .subscribe("foo")
                      .apply();

        verify(mockDispatcher, times(0)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
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
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    /**
     * Test editAttribute's apply function dispatches an update job and saves attributes to
     * the registrar.
     */
    @Test
    public void testAttributesUpdates() {
        clock.currentTimeMillis = 100;

        airshipChannel.editAttributes()
                      .setAttribute("expected_key", "expected_value")
                      .apply();

        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);
        List<AttributeMutation> expectedMutations = Collections.singletonList(mutation);
        verify(mockAttributeRegistrar).addPendingMutations(expectedMutations);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    /**
     * Test editSubscriptionLists function dispatches an update job and saves list mutations to the registrar.
     */
    @Test
    public void testSubscriptionListUpdates() {
        clock.currentTimeMillis = 100;

        airshipChannel.editSubscriptionLists()
                      .subscribe("foo")
                      .unsubscribe("bar")
                      .apply();

        SubscriptionListMutation mutation1 = SubscriptionListMutation.newSubscribeMutation("foo", 100L);
        SubscriptionListMutation mutation2 = SubscriptionListMutation.newUnsubscribeMutation("bar", 100L);
        List<SubscriptionListMutation> expectedMutations = Arrays.asList(mutation1, mutation2);

        verify(mockSubscriptionListRegistrar).addPendingMutations(expectedMutations);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    /**
     * Test editTagGroups apply does not update the tag groups if addTags and removeTags are empty.
     */
    @Test
    public void testEmptyTagGroupUpdate() {
        airshipChannel.editTagGroups().apply();
        verifyNoInteractions(mockDispatcher);
    }

    /**
     * Test empty editAttribute's apply function doesn't generate a call to update the attributes.
     */
    @Test
    public void testEmptyAttributeUpdates() {
        airshipChannel.editAttributes().apply();
        verifyNoInteractions(mockDispatcher);
    }

    /**
     * Test empty editSubscriptionList's apply function doesn't generate a call to update if no
     * mutations were requested.
     */
    @Test
    public void testEmptySubscriptionListsUpdate() {
        airshipChannel.editSubscriptionLists().apply();
        verifyNoInteractions(mockDispatcher);
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
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
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
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
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
     * Tests that subscription lists are returned from the in-memory cache when available.
     */
    @Test
    public void testGetSubscriptionListsFromCache() {
        Set<String> subscriptions = new HashSet<String>() {{
            add("foo");
            add("bar");
        }};

        assertNull(subscriptionListCache.get());

        subscriptionListCache.set(subscriptions, 100);

        PendingResult<Set<String>> result = airshipChannel.getSubscriptionLists(false);
        shadowMainLooper().idle();

        result.addResultCallback(result1 -> {
            assertEquals(subscriptions, result1);
        });

        verify(mockSubscriptionListRegistrar, never()).fetchChannelSubscriptionLists();
    }

    /**
     * Tests that subscription lists are fetched from the network when the cache is expired.
     */
    @Test
    public void testGetSubscriptionListsFromNetworkIfCacheExpired() {
        final Set<String> cachedSubscriptions = new HashSet<String>() {{
            add("foo");
            add("bar");
        }};

        final Set<String> networkSubscriptions = new HashSet<String>() {{
            addAll(cachedSubscriptions);
            add("fizz");
            add("buzz");
        }};

        when(mockSubscriptionListRegistrar.fetchChannelSubscriptionLists()).thenReturn(networkSubscriptions);

        // Prime the cache
        clock.currentTimeMillis = 100;
        subscriptionListCache.set(cachedSubscriptions, 10);

        // Advance the time past the subscription cache lifetime
        clock.currentTimeMillis += 10;
        assertNull(subscriptionListCache.get());

        PendingResult<Set<String>> result = airshipChannel.getSubscriptionLists(false);
        result.addResultCallback(new ResultCallback<Set<String>>() {
            @Override
            public void onResult(@Nullable Set<String> result) {
                assertEquals(networkSubscriptions, result);

                // Verify that the cache was updated
                assertEquals(networkSubscriptions, subscriptionListCache.get());

                verify(mockSubscriptionListRegistrar).fetchChannelSubscriptionLists();
            }
        });

        shadowOf(getMainLooper()).idle();
    }

    /**
     * Tests that subscription lists are fetched from the network when the cache is empty.
     */
    @Test
    public void testGetSubscriptionListsFromNetworkIfCacheEmpty() {
        final Set<String> cachedSubscriptions = new HashSet<String>() {{
            add("foo");
            add("bar");
        }};

        final Set<String> networkSubscriptions = new HashSet<String>() {{
            addAll(cachedSubscriptions);
            add("fizz");
            add("buzz");
        }};

        when(mockSubscriptionListRegistrar.fetchChannelSubscriptionLists()).thenReturn(networkSubscriptions);

        // Ensure the cache is empty
        assertNull(subscriptionListCache.get());

        PendingResult<Set<String>> result = airshipChannel.getSubscriptionLists(false);
        result.addResultCallback(new ResultCallback<Set<String>>() {
            @Override
            public void onResult(@Nullable Set<String> result) {
                assertEquals(networkSubscriptions, result);

                // Verify that the cache was updated
                assertEquals(networkSubscriptions, subscriptionListCache.get());

                verify(mockSubscriptionListRegistrar).fetchChannelSubscriptionLists();
            }
        });

        shadowOf(getMainLooper()).idle();
    }

    /**
     * Test that pending subscription list updates are applied to the set returned by
     * getSubscriptionLists(true).
     */
    @Test
    public void testGetSubscriptionListsIncludesPending() {
        final Set<String> initialSubscriptions = new HashSet<String>() {{
            add("foo");
            add("bar");
        }};
        final Set<String> subscriptionsWithPending = new HashSet<String>() {{
            add("fizz");
            add("buzz");
        }};
        List<SubscriptionListMutation> pendingMutations = new ArrayList<SubscriptionListMutation>() {{
            add(SubscriptionListMutation.newSubscribeMutation("fizz", 0L));
            add(SubscriptionListMutation.newSubscribeMutation("buzz", 0L));
            add(SubscriptionListMutation.newUnsubscribeMutation("foo", 0L));
            add(SubscriptionListMutation.newUnsubscribeMutation("bar", 0L));
        }};

        when(mockSubscriptionListRegistrar.fetchChannelSubscriptionLists()).thenReturn(initialSubscriptions);
        when(mockSubscriptionListRegistrar.getPendingMutations()).thenReturn(pendingMutations);

        PendingResult<Set<String>> result = airshipChannel.getSubscriptionLists(true);
        result.addResultCallback(new ResultCallback<Set<String>>() {
            @Override
            public void onResult(@Nullable Set<String> subscriptions) {
                assertEquals(subscriptionsWithPending, subscriptions);

                // Verify that pending updates weren't stored in the cache after being applied.
                assertEquals(initialSubscriptions, subscriptionListCache.get());

                verify(mockSubscriptionListRegistrar).fetchChannelSubscriptionLists();
                verify(mockSubscriptionListRegistrar).getPendingMutations();
            }
        });

        shadowOf(getMainLooper()).idle();
    }

    /**
     * Test delay channel creation.
     */
    @Test
    public void testDelayChannelCreation() {
        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(false)
                .build();

        runtimeConfig.setConfigOptions(configOptions);

        airshipChannel = new AirshipChannel(getApplication(), dataStore,
                runtimeConfig, privacyManager, localeManager, mockDispatcher, clock,
                mockClient, mockAttributeRegistrar, mockTagGroupRegistrar, mockSubscriptionListRegistrar,
                subscriptionListCache, testActivityMonitor);

        airshipChannel.init();
        assertFalse(airshipChannel.isChannelCreationDelayEnabled());

        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();

        runtimeConfig.setConfigOptions(configOptions);

        airshipChannel = new AirshipChannel(getApplication(), dataStore,
                runtimeConfig, privacyManager, localeManager, mockDispatcher, clock,
                mockClient, mockAttributeRegistrar, mockTagGroupRegistrar, mockSubscriptionListRegistrar,
                subscriptionListCache, testActivityMonitor);

        airshipChannel.init();
        assertTrue(airshipChannel.isChannelCreationDelayEnabled());
    }

    /**
     * Test enable channel creation.
     */
    @Test
    public void testEnableChannelCreation() {
        // Enable channel delay
        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCreationDelayEnabled(true)
                .build();

        runtimeConfig.setConfigOptions(configOptions);

        airshipChannel = new AirshipChannel(getApplication(), dataStore,
                runtimeConfig, privacyManager, localeManager, mockDispatcher, clock,
                mockClient, mockAttributeRegistrar, mockTagGroupRegistrar, mockSubscriptionListRegistrar,
                subscriptionListCache, testActivityMonitor);

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
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    @Test
    public void testDataCollectionUpdatesRegistration() {
        airshipChannel.init();
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ALL);
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") && jobInfo.getConflictStrategy() == JobInfo.KEEP;
            }
        }));
    }

    @Test
    public void testOptingChannelOut() throws RequestException {
        when(mockClient.createChannelWithPayload(any(ChannelRegistrationPayload.class)))
                .thenReturn(createResponse("channel", 200));
        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);

        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);

        // Should be ignored
        airshipChannel.addChannelRegistrationPayloadExtender(new AirshipChannel.ChannelRegistrationPayloadExtender() {
            @NonNull
            @Override
            public ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder) {
                return builder.setDeviceModel(UUID.randomUUID().toString());
            }
        });

        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(false)
                .setDeviceType(ChannelRegistrationPayload.ANDROID_DEVICE_TYPE)
                .build();

        when(mockClient.updateChannelWithPayload("channel", payload))
                .thenReturn(AirshipChannelTests.<Void>createResponse(null, 200));

        airshipChannel.onPerformJob(UAirship.shared(), UPDATE_CHANNEL_JOB);
        verify(mockClient, times(1)).updateChannelWithPayload("channel", payload);
    }

    @Test
    public void testNewUrlConfigUpdatesRegistrationFully() {
        clearInvocations(mockDispatcher);

        airshipChannel.onUrlConfigUpdated();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                JsonValue extraForceFullUpdate = jobInfo.getExtras().get("EXTRA_FORCE_FULL_UPDATE");
                return jobInfo.getAction().equals("ACTION_UPDATE_CHANNEL") &&
                        extraForceFullUpdate != null &&
                        extraForceFullUpdate.getBoolean(false) &&
                        jobInfo.getConflictStrategy() == JobInfo.REPLACE;
            }
        }));
    }

    @Test
    public void testProcessContactSubscriptionListChanges() {
        List<SubscriptionListMutation> updates = new ArrayList<SubscriptionListMutation>() {{
            add(SubscriptionListMutation.newSubscribeMutation("app 1", 100));
            add(SubscriptionListMutation.newUnsubscribeMutation("app 2", 100));
        }};

        airshipChannel.processContactSubscriptionListMutations(updates);

        verify(mockSubscriptionListRegistrar).cacheInLocalHistory(updates);
    }

    private static <T> Response<T> createResponse(T result, int status) {
        return new Response.Builder<T>(status)
                .setResponseBody("test")
                .setResult(result)
                .build();
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
