/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonMap;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.shadow.ShadowNotificationManagerExtension;
import com.urbanairship.util.Network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Config(
        sdk = 28,
        shadows = { ShadowNotificationManagerExtension.class, ShadowAirshipExecutorsLegacy.class }
)
@LooperMode(LooperMode.Mode.LEGACY)
public class RemoteDataTest extends BaseTestCase {

    private RemoteData remoteData;
    private PreferenceDataStore preferenceDataStore;
    private JobDispatcher mockDispatcher;
    private TestActivityMonitor activityMonitor;
    private LocaleManager localeManager;
    private PushManager pushManager;
    private PushListener pushListener;
    private TestClock clock;
    private RemoteDataApiClient mockClient;
    private RemoteDataPayload payload;
    private RemoteDataPayload otherPayload;
    private RemoteDataPayload emptyPayload;
    private PrivacyManager privacyManager;
    private Network mockNetwork = mock(Network.class);
    private RemoteDataUrlFactory mockUrlFactory = mock(RemoteDataUrlFactory.class);

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();

        mockDispatcher = mock(JobDispatcher.class);
        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;

        privacyManager = new PrivacyManager(preferenceDataStore, PrivacyManager.FEATURE_ALL);

        localeManager = new LocaleManager(getApplication(), preferenceDataStore);

        pushManager = mock(PushManager.class);

        clock = new TestClock();

        mockClient = mock(RemoteDataApiClient.class);
        when(mockUrlFactory.createAppUrl(any(Locale.class), anyInt())).thenReturn(Uri.parse("https://airship.com/example-remote-data"));

        remoteData = new RemoteData(TestApplication.getApplication(), preferenceDataStore, TestAirshipRuntimeConfig.newTestConfig(),
                privacyManager, activityMonitor, mockDispatcher, localeManager, pushManager, clock, mockClient, mockNetwork, mockUrlFactory);

        ArgumentCaptor<PushListener> pushListenerArgumentCaptor = ArgumentCaptor.forClass(PushListener.class);
        remoteData.init();
        verify(pushManager).addInternalPushListener(pushListenerArgumentCaptor.capture());
        pushListener = pushListenerArgumentCaptor.getValue();


        payload = new RemoteDataPayload(
                "type",
                123,
                JsonMap.newBuilder()
                       .put("foo", "bar")
                       .build(),
                new RemoteDataInfo("some url", "some last modified", RemoteDataSource.APP)
        );

        otherPayload = new RemoteDataPayload(
                "otherType",
                123,
                JsonMap.newBuilder()
                       .put("baz", "boz")
                       .build(),
                null
        );

        emptyPayload = RemoteDataPayload.emptyPayload("type");
    }

    @After
    public void teardown() {
        remoteData.tearDown();
        preferenceDataStore.tearDown();
    }

    @Test
    public void testOnPushReceived() {
        clearInvocations(mockDispatcher);

        Map<String, String> pushData = new HashMap<>();
        pushData.put(PushMessage.REMOTE_DATA_UPDATE_KEY, "remoteDataUpdate");
        PushMessage message = new PushMessage(pushData);

        pushListener.onPushReceived(message, true);

        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test that a transition to foreground results in a refresh.
     */
    @Test
    public void testForegroundTransition() {
        clearInvocations(mockDispatcher);
        activityMonitor.foreground();

        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test that a locale change results in a refresh.
     */
    @Test
    public void testLocaleChangeRefresh() {
        activityMonitor.foreground();
        clearInvocations(mockDispatcher);

        localeManager.setLocaleOverride(new Locale("de"));

        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH)));

        verifyNoMoreInteractions(mockDispatcher);
    }

    @Test
    public void testCheckRefreshFeaturesDisabled() {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        activityMonitor.foreground();
        verifyNoInteractions(mockDispatcher);
    }

    @Test
    public void testRefreshFeaturesEnabled() {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        activityMonitor.foreground();

        verifyNoInteractions(mockDispatcher);

        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ANALYTICS);
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH)));
    }

    /**
     * Test that a url config update results in a refresh.
     */
    @Test
    public void testUrlConfigUpdateRefresh() {
        clearInvocations(mockDispatcher);

        remoteData.onUrlConfigUpdated();

        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.REPLACE));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test a foreground transition will not trigger a refresh if its before the foreground refresh
     * interval.
     */
    @Test
    public void testRefreshInterval() throws RequestException {
        // Refresh
        clock.currentTimeMillis = 100;

        updatePayloads();

        // Set foreground refresh interval to 10 ms
        remoteData.setForegroundRefreshInterval(10);

        // Time travel 9 ms, should skip refresh on foreground
        clock.currentTimeMillis += 9;
        activityMonitor.foreground();
        verify(mockDispatcher, never()).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));

        // Time travel 1 ms, should refresh on foreground
        clock.currentTimeMillis += 1;
        activityMonitor.foreground();
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));
    }

    /**
     * Test privacy manager only updates remote-data if it has not been updated during the session.
     */
    @Test
    public void testPrivacyManagerChanges() throws RequestException {
        activityMonitor.foreground();

        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));

        updatePayloads();

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test privacy manager triggers updates on change.
     */
    @Test
    public void testPrivacyManagerTriggersUpdates() {
        activityMonitor.foreground();
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        verify(mockDispatcher, times(2)).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.KEEP));
    }

    /**
     * Test that an empty cache will result in empty model objects in the initial callback.
     */
    @Test
    public void testPayloadsForTypeWithEmptyCache() throws RequestException {
        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(@NonNull RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // The first callback should be an empty placeholder
        assertEquals(1, subscribedPayloads.size());
        assertEquals(RemoteDataPayload.emptyPayload("type"), subscribedPayloads.get(0));

        subscribedPayloads.clear();

        updatePayloads(payload);

        // The second callback should be the refreshed data
        assertEquals(1, subscribedPayloads.size());
        assertEquals(payload, subscribedPayloads.get(0));
    }

    /**
     * Test that a type missing in the response will result in an empty model object in the callback.
     */
    @Test
    public void testPayloadsForTypeWithMissingTypeInResponse() throws RequestException {
        updatePayloads(payload);

        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(@NonNull RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();
        subscribedPayloads.clear();

        updatePayloads(otherPayload);
        runLooperTasks();

        assertEquals(1, subscribedPayloads.size());
        assertEquals(emptyPayload, subscribedPayloads.get(0));
    }

    /**
     * Test that a single type will only produce a callback if the payload has changed
     */
    @Test
    public void testPayloadsForTypeDistinctness() throws RequestException {
        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();
        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");
        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(@NonNull RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();
        subscribedPayloads.clear();

        updatePayloads(payload);

        assertEquals(1, subscribedPayloads.size(), 1);
        subscribedPayloads.clear();

        updatePayloads(payload);
        assertEquals(0, subscribedPayloads.size());
    }

    /**
     * Test that a type missing in the response will result in empty model objects in the callback.
     */
    @Test
    public void testPayloadsForTypesWithMissingTypeInResponse() throws Exception {

        updatePayloads(payload, otherPayload);
        runLooperTasks();

        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();
        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));

        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
            @Override
            public void onNext(@NonNull Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        assertEquals(subscribedPayloads.size(), 1);
        assertEquals(asSet(payload, otherPayload), subscribedPayloads.get(0));

        subscribedPayloads.clear();

        // Remove the payload
        updatePayloads(otherPayload);

        assertEquals(1, subscribedPayloads.size());
        assertEquals(asSet(otherPayload, emptyPayload), subscribedPayloads.get(0));
    }

    /**
     * Test that a multiple types will only produce a callback if at least one of the payloads has changed
     */
    @Test
    public void testPayloadsForTypesDistinctness() throws RequestException {
        final List<Set<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));
        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
            @Override
            public void onNext(@NonNull Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(new HashSet<>(value));
            }
        });

        runLooperTasks();

        // Clear the first callback
        subscribedPayloads.clear();

        updatePayloads(payload, otherPayload);
        assertEquals(asSet(payload, otherPayload), subscribedPayloads.get(0));

        // Replaying the response should not result in another callback because the timestamp hasn't changed
        updatePayloads(payload, otherPayload);
        assertEquals(1, subscribedPayloads.size());

        subscribedPayloads.clear();

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = new RemoteDataPayload(
                payload.getType(),
                payload.getTimestamp() + 100000,
                payload.getData(),
                null
        );

        updatePayloads(freshPayload, otherPayload);
        assertEquals(asSet(freshPayload, otherPayload), subscribedPayloads.get(0));
    }

    @Test
    public void testLastModified() throws RequestException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Last-Modified", "lastModifiedResponse");

        Response<RemoteDataApiClient.Result> response = new Response<>(200,
                new RemoteDataApiClient.Result(createMetadata("lastModifiedResponse"), asSet(payload)),
                null,
                headers
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
        runLooperTasks();

        // Perform the update
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
        runLooperTasks();

        verify(mockClient).fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                eq("lastModifiedResponse"),
                any()
        );
    }

    /**
     * Test last modified is ignored if the metadata changes.
     */
    @Test
    public void testLastModifiedMetadataChanges() throws RequestException {
        Locale locale = Locale.forLanguageTag("en-US");
        Locale otherLocale = Locale.forLanguageTag("de-de");
        when(mockUrlFactory.createAppUrl(otherLocale, 555)).thenReturn(Uri.parse("https://airship.com/some-locale"));
        when(mockUrlFactory.createAppUrl(locale, 555)).thenReturn(Uri.parse("https://airship.com/some-locale"));

        localeManager.setLocaleOverride(locale);

        Map<String, String> headers = new HashMap<>();
        headers.put("Last-Modified", "lastModifiedResponse");

        Response<RemoteDataApiClient.Result> response = new Response<>(200,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload)),
                null,
                headers
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
        runLooperTasks();

        localeManager.setLocaleOverride(otherLocale);

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                eq((String) null),
                any()
        )).thenReturn(response);

        // Perform the update
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
        runLooperTasks();
    }

    /**
     * Test that fetching remote data succeeds if the status is 200
     */
    @Test
    public void testRefreshRemoteData200() throws RequestException {
        Response<RemoteDataApiClient.Result> response = new Response<>(200,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
    }

    @Test
    public void testRefreshWhenFeaturesDisabled() {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));

        verifyNoMoreInteractions(mockClient);
    }

    /**
     * Test that fetching remote data succeeds if the status is 304
     */
    @Test
    public void testRefreshRemoteData304() throws RequestException {
        Response<RemoteDataApiClient.Result> response = new Response<>(304,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);


        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * Test that fetching remote data job retries if the status is 5xx
     */
    @Test
    public void testRefreshRemoteDataServerError() throws RequestException {
        Response<RemoteDataApiClient.Result> response = new Response<>(
                500,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.RETRY, remoteData.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * Test that fetching remote data job finishes if the status is 4xx
     */
    @Test
    public void testRefreshRemoteDataClientError() throws RequestException {
        Response<RemoteDataApiClient.Result> response = new Response<>(400,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));
    }

    @Test
    public void testRefresh() throws RequestException, ExecutionException, InterruptedException {
        when(mockNetwork.isConnected(any(Context.class))).thenReturn(true);
        activityMonitor.foreground();
        clearInvocations(mockDispatcher);

        PendingResult<Boolean> pendingResult = remoteData.refresh();
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.REPLACE));

        assertFalse(pendingResult.isDone());

        Response<RemoteDataApiClient.Result> response = new Response<>(200,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));

        assertTrue(pendingResult.isDone());
        assertTrue(pendingResult.get());
    }

    @Test
    public void testRefreshShouldSkip() throws ExecutionException, InterruptedException {
        verifyNoMoreInteractions(mockDispatcher);
        PendingResult<Boolean> pendingResult = remoteData.refresh();

        assertTrue(pendingResult.isDone());
        assertTrue(pendingResult.get());
    }

    @Test
    public void testForceRefresh() throws RequestException, ExecutionException, InterruptedException {
        when(mockNetwork.isConnected(any(Context.class))).thenReturn(true);
        PendingResult<Boolean> pendingResult = remoteData.refresh(true);
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.REPLACE));

        assertFalse(pendingResult.isDone());

        Response<RemoteDataApiClient.Result> response = new Response<>(200,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payload))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));

        assertTrue(pendingResult.isDone());
        assertTrue(pendingResult.get());
    }

    @Test
    public void testRefreshFailed() throws RequestException, ExecutionException, InterruptedException {
        when(mockNetwork.isConnected(any(Context.class))).thenReturn(true);
        PendingResult<Boolean> pendingResult = remoteData.refresh(true);
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(RemoteData.ACTION_REFRESH) && jobInfo.getConflictStrategy() == JobInfo.REPLACE));

        assertFalse(pendingResult.isDone());

        Response<RemoteDataApiClient.Result> response = new Response<>(400, null);

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));

        assertTrue(pendingResult.isDone());
        assertFalse(pendingResult.get());
    }

    @Test
    public void testRefreshNoNetwork() throws ExecutionException, InterruptedException {
        when(mockNetwork.isConnected(any(Context.class))).thenReturn(false);
        verifyNoMoreInteractions(mockDispatcher);
        PendingResult<Boolean> pendingResult = remoteData.refresh(true);

        assertTrue(pendingResult.isDone());
        assertFalse(pendingResult.get());
    }

    private void updatePayloads(RemoteDataPayload... payloads) throws RequestException {
        Response<RemoteDataApiClient.Result> response = new Response<>(200,
                new RemoteDataApiClient.Result(createMetadata(null), asSet(payloads))
        );

        when(mockClient.fetch(
                eq(Uri.parse("https://airship.com/example-remote-data")),
                eq(RequestAuth.BasicAppAuth.INSTANCE),
                nullable(String.class),
                any()
        )).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build();
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(UAirship.shared(), jobInfo));

        runLooperTasks();
    }

    /**
     * Helper method to run all the looper tasks.
     */
    private void runLooperTasks() {
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        ShadowLooper backgroundLooper = Shadows.shadowOf(remoteData.backgroundThread.getLooper());

        do {
            mainLooper.runToEndOfTasks();
            backgroundLooper.runToEndOfTasks();
        }
        while (mainLooper.getScheduler().areAnyRunnable() || backgroundLooper.getScheduler().areAnyRunnable());
    }

    private static <T> Set<T> asSet(T... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private static RemoteDataInfo createMetadata(String lastModified) {
        return new RemoteDataInfo("https://airship.com/example-remote-data", lastModified, RemoteDataSource.APP);

    }
}
