/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.os.Looper;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.UAirship;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Subscriber;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.Shadows;
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

import androidx.annotation.NonNull;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RemoteDataTest extends BaseTestCase {

    private RemoteData remoteData;
    private PreferenceDataStore preferenceDataStore;
    private AirshipConfigOptions options;
    private JobDispatcher mockDispatcher;
    private TestActivityMonitor activityMonitor;
    private RemoteDataPayload payload;
    private RemoteDataPayload otherPayload;
    private RemoteDataPayload emptyPayload;
    private LocaleManager localeManager;
    private PushManager pushManager;
    private PushListener pushListener;
    private TestClock clock;

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();

        mockDispatcher = mock(JobDispatcher.class);
        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;

        localeManager = new LocaleManager(getApplication(), preferenceDataStore);

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        pushManager = mock(PushManager.class);

        clock = new TestClock();

        remoteData = new RemoteData(TestApplication.getApplication(), preferenceDataStore, options,
                activityMonitor, mockDispatcher, localeManager, pushManager, clock);
        payload = RemoteDataPayload.newBuilder()
                                   .setType("type")
                                   .setTimeStamp(123)
                                   .setData(JsonMap.newBuilder()
                                                   .put("foo", "bar")
                                                   .build())
                                   .build();
        otherPayload = RemoteDataPayload.newBuilder()
                                        .setType("otherType")
                                        .setTimeStamp(234)
                                        .setData(JsonMap.newBuilder()
                                                        .put("baz", "boz")
                                                        .build())
                                        .build();
        emptyPayload = RemoteDataPayload.emptyPayload("type");

        ArgumentCaptor<PushListener> pushListenerArgumentCaptor = ArgumentCaptor.forClass(PushListener.class);
        remoteData.init();
        verify(pushManager).addInternalPushListener(pushListenerArgumentCaptor.capture());
        pushListener = pushListenerArgumentCaptor.getValue();
    }

    @After
    public void teardown() {
        remoteData.tearDown();
    }

    @Test
    public void testOnPushReceived() {
        clearInvocations(mockDispatcher);

        Map<String, String> pushData = new HashMap<>();
        pushData.put(PushMessage.REMOTE_DATA_UPDATE_KEY, "remoteDataUpdate");
        PushMessage message = new PushMessage(pushData);

        pushListener.onPushReceived(message, true);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(RemoteDataJobHandler.ACTION_REFRESH);
            }
        }));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test that a transition to foreground results in a refresh.
     */
    @Test
    public void testForegroundTransition() {
        clearInvocations(mockDispatcher);
        activityMonitor.foreground();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(RemoteDataJobHandler.ACTION_REFRESH);
            }
        }));

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

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(RemoteDataJobHandler.ACTION_REFRESH);
            }
        }));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test a foreground transition will not trigger a refresh if its before the foreground refresh
     * interval.
     */
    @Test
    public void testRefreshInterval() {
        // Refresh
        clock.currentTimeMillis = 100;
        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", RemoteData.createMetadata(localeManager.getLocale()));
        remoteData.onRefreshFinished();
        runLooperTasks();

        // Set foreground refresh interval to 10 ms
        remoteData.setForegroundRefreshInterval(10);

        // Time travel 9 ms, should skip refresh on foreground
        clock.currentTimeMillis += 9;
        activityMonitor.foreground();
        verify(mockDispatcher, never()).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(RemoteDataJobHandler.ACTION_REFRESH);
            }
        }));

        // Time travel 1 ms, should refresh on foreground
        clock.currentTimeMillis += 1;
        activityMonitor.foreground();
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(RemoteDataJobHandler.ACTION_REFRESH);
            }
        }));
    }

    /**
     * Test that an empty cache will result in empty model objects in the initial callback.
     */
    @Test
    public void testPayloadsForTypeWithEmptyCache() {
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
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), emptyPayload);

        subscribedPayloads.clear();

        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        // The second callback should be the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);
    }

    /**
     * Test that a type missing in the response will result in an empty model object in the callback.
     */
    @Test
    public void testPayloadsForTypeWithMissingTypeInResponse() {
        remoteData.dataStore.savePayloads(asSet(payload));

        Assert.assertEquals(remoteData.dataStore.getPayloads().size(), 1);

        runLooperTasks();

        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(@NonNull RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // The first callback should be the cached payload
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);

        subscribedPayloads.clear();

        remoteData.onNewData(asSet(otherPayload), "lastModified", JsonMap.EMPTY_MAP);

        runLooperTasks();

        // The second callback should be an empty placeholder
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), emptyPayload);
    }

    /**
     * Test that a single type will only produce a callback if the payload has changed
     */
    @Test
    public void testPayloadsForTypeDistinctness() {
        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(@NonNull RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // Clear the first callback
        subscribedPayloads.clear();

        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        // We should get a second callback with the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);

        subscribedPayloads.clear();

        // Replaying the response should not result in another callback because the data hasn't changed
        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();
        Assert.assertEquals(subscribedPayloads.size(), 0);

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = RemoteDataPayload.newBuilder()
                                                          .setType(payload.getType())
                                                          .setTimeStamp(payload.getTimestamp() + 100000)
                                                          .setData(payload.getData())
                                                          .build();

        remoteData.onNewData(asSet(freshPayload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), freshPayload);
    }

    /**
     * Test that an empty cache will result in empty model objects in the initial callback.
     */
    @Test
    public void testPayloadsForTypesWithEmptyCache() {
        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));

        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
            @Override
            public void onNext(@NonNull Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // The first callback should be an empty placeholder
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Collection<RemoteDataPayload> collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection.size(), 2);

        for (RemoteDataPayload payload : collection) {
            Assert.assertEquals(payload.getTimestamp(), 0);
        }

        subscribedPayloads.clear();

        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        // The second callback should be the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection.size(), 2);
        Assert.assertEquals(collection, asSet(payload, otherPayload));
    }

    /**
     * Test that a type missing in the response will result in empty model objects in the callback.
     */
    @Test
    public void testPayloadsForTypesWithMissingTypeInResponse() throws Exception {
        remoteData.dataStore.savePayloads(asSet(payload, otherPayload));

        Assert.assertEquals(remoteData.dataStore.getPayloads().size(), 2);

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

        // The first callback should be the cached payload
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Collection<RemoteDataPayload> collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection, asSet(payload, otherPayload));

        subscribedPayloads.clear();

        RemoteDataPayload freshOtherPayload = RemoteDataPayload.newBuilder()
                                                               .setType(otherPayload.getType())
                                                               .setTimeStamp(otherPayload.getTimestamp() + 100000)
                                                               .setData(otherPayload.getData())
                                                               .build();

        remoteData.onNewData(asSet(freshOtherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        // The second callback should have an empty placeholder for the first payload
        Assert.assertEquals(subscribedPayloads.size(), 1);
        collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection, asSet(emptyPayload, freshOtherPayload));
    }

    /**
     * Test that a multiple types will only produce a callback if at least one of the payloads has changed
     */
    @Test
    public void testPayloadsForTypesDistinctness() {
        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));
        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
            @Override
            public void onNext(@NonNull Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // Clear the first callback
        subscribedPayloads.clear();

        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads, Arrays.asList(asSet(payload, otherPayload)));

        // Replaying the response should not result in another callback because the timestamp hasn't changed
        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();
        Assert.assertEquals(subscribedPayloads, Arrays.asList(asSet(payload, otherPayload)));

        subscribedPayloads.clear();

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = RemoteDataPayload.newBuilder()
                                                          .setType(payload.getType())
                                                          .setTimeStamp(payload.getTimestamp() + 100000)
                                                          .setData(payload.getData())
                                                          .build();

        remoteData.onNewData(asSet(freshPayload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads, Arrays.asList(asSet(freshPayload, otherPayload)));
    }

    /**
     * Test last modified is updating when onNewData is called.
     */
    @Test
    public void testLastModified() {
        remoteData.onNewData(asSet(otherPayload), "lastModified", RemoteData.createMetadata(localeManager.getLocale()));
        runLooperTasks();

        Assert.assertEquals(remoteData.getLastModified(), "lastModified");
    }

    /**
     * Test last modified is ignored if the metadata changes.
     */
    @Test
    public void testLastModifiedMetadataChanges() {
        remoteData.onNewData(asSet(otherPayload), "lastModified", RemoteData.createMetadata(localeManager.getLocale()));
        runLooperTasks();

        Assert.assertEquals("lastModified", remoteData.getLastModified());

        UAirship.shared().setLocaleOverride(new Locale("de"));
        Assert.assertNull(remoteData.getLastModified());
    }

    /**
     * Test the refresh response callback from the job runner.
     */
    @Test
    public void testHandleRefreshResponse() {
        final Set<RemoteDataPayload> subscribedPayloads = new HashSet<>();

        remoteData.payloadUpdates.subscribe(new Subscriber<Set<RemoteDataPayload>>() {
            @Override
            public void onNext(@NonNull Set<RemoteDataPayload> payloads) {
                subscribedPayloads.addAll(payloads);
            }
        });

        remoteData.onNewData(asSet(payload, otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        Assert.assertEquals(asSet(payload, otherPayload), subscribedPayloads);
        Assert.assertEquals(remoteData.dataStore.getPayloads(), asSet(payload, otherPayload));

        subscribedPayloads.clear();

        // Subsequent refresh response missing previously known types
        remoteData.onNewData(asSet(otherPayload), "lastModified", JsonMap.EMPTY_MAP);
        runLooperTasks();

        Assert.assertEquals(asSet(otherPayload), subscribedPayloads);

        // "Deleted" payload types should not persist in the cache
        Assert.assertEquals(remoteData.dataStore.getPayloads(), asSet(otherPayload));
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
        return new HashSet<T>(Arrays.asList(items));
    }

}
