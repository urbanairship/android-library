/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.os.Looper;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Subscriber;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
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

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();

        mockDispatcher = mock(JobDispatcher.class);
        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        remoteData = new RemoteData(TestApplication.getApplication(), preferenceDataStore, options, activityMonitor, mockDispatcher);

        payload = new RemoteDataPayload("type", 123, JsonMap.newBuilder().put("foo", "bar").build());
        otherPayload = new RemoteDataPayload("otherType", 234, JsonMap.newBuilder().put("baz", "boz").build());
        emptyPayload = new RemoteDataPayload("type", 0, JsonMap.newBuilder().build());
    }

    @After
    public void teardown() {
        remoteData.tearDown();
        activityMonitor.unregister();
    }

    /**
     * Test that a transition to foreground results in a refresh.
     */
    @Test
    public void testForegroundTransition() {
        remoteData.init();
        clearInvocations(mockDispatcher);
        activityMonitor.startActivity();

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
        remoteData.init();
        clearInvocations(mockDispatcher);
        remoteData.setForegroundRefreshInterval(100000);


        activityMonitor.startActivity();
        activityMonitor.stopActivity();


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
        remoteData.init();
        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // The first callback should be an empty placeholder
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), emptyPayload);

        subscribedPayloads.clear();

        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
        runLooperTasks();

        // The second callback should be the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);
    }

    /**
     * Test that a type missing in the response will result in an empty model object in the callback.
     */
    @Test
    public void testPayloadsForTypeWithMissingTypeInResponse() throws Exception {
        remoteData.init();
        remoteData.dataStore.savePayload(payload);

        Assert.assertEquals(remoteData.dataStore.getPayloads().size(), 1);

        runLooperTasks();

        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // The first callback should be the cached payload
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);

        subscribedPayloads.clear();

        remoteData.handleRefreshResponse(asSet(otherPayload));

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
        remoteData.init();

        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        Observable<RemoteDataPayload> payloadsObservable = remoteData.payloadsForType("type");

        payloadsObservable.subscribe(new Subscriber<RemoteDataPayload>() {
            @Override
            public void onNext(RemoteDataPayload value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // Clear the first callback
        subscribedPayloads.clear();

        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
        runLooperTasks();

        // We should get a second callback with the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);

        subscribedPayloads.clear();

        // Replaying the response should not result in another callback because the data hasn't changed
        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
        runLooperTasks();
        Assert.assertEquals(subscribedPayloads.size(), 0);

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = new RemoteDataPayload(payload.getType(), payload.getTimestamp() + 100000, payload.getData());
        remoteData.handleRefreshResponse(asSet(freshPayload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), freshPayload);
    }

    /**
     * Test that an empty cache will result in empty model objects in the initial callback.
     */
    @Test
    public void testPayloadsForTypesWithEmptyCache() {
        remoteData.init();
        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));

        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>(){
            @Override
            public void onNext(Collection<RemoteDataPayload> value) {
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

        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
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
        remoteData.init();
        remoteData.dataStore.savePayload(payload);
        remoteData.dataStore.savePayload(otherPayload);

        Assert.assertEquals(remoteData.dataStore.getPayloads().size(), 2);

        runLooperTasks();

        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));

        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
            @Override
            public void onNext(Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // The first callback should be the cached payload
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Collection<RemoteDataPayload> collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection, asSet(payload, otherPayload));

        subscribedPayloads.clear();

        RemoteDataPayload freshOtherPayload = new RemoteDataPayload(otherPayload.getType(), otherPayload.getTimestamp() + 10000, otherPayload.getData());

        remoteData.handleRefreshResponse(asSet(freshOtherPayload));
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
        remoteData.init();

        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes(Arrays.asList("type", "otherType"));
        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>(){
            @Override
            public void onNext(Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // Clear the first callback
        subscribedPayloads.clear();

        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads, Arrays.asList(asSet(payload, otherPayload)));

        // Replaying the response should not result in another callback because the timestamp hasn't changed
        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
        runLooperTasks();
        Assert.assertEquals(subscribedPayloads, Arrays.asList(asSet(payload, otherPayload)));

        subscribedPayloads.clear();

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = new RemoteDataPayload(payload.getType(), payload.getTimestamp() + 100000, payload.getData());
        remoteData.handleRefreshResponse(asSet(freshPayload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads, Arrays.asList(asSet(freshPayload, otherPayload)));
    }

    /**
     * Test setting the last modified timestamp.
     */
    @Test
    public void testSetLastModified() {
        remoteData.setLastModified("lastModified");
        Assert.assertEquals(preferenceDataStore.getString("com.urbanairship.remotedata.LAST_MODIFIED", ""), "lastModified");
    }

    /**
     * Test getting the last modified timestamp.
     */
    @Test
    public void testGetLastModified() {
        remoteData.setLastModified("lastModified again");
        Assert.assertEquals(remoteData.getLastModified(), "lastModified again");
    }

    /**
     * Test the refresh response callback from the job runner.
     */
    @Test
    public void testHandleRefreshResponse() {
        remoteData.init();

        final Set<RemoteDataPayload> subscribedPayloads = new HashSet<>();

        remoteData.payloadUpdates.subscribe(new Subscriber<Set<RemoteDataPayload>>() {
            @Override
            public void onNext(Set<RemoteDataPayload> payloads) {
                subscribedPayloads.addAll(payloads);
            }
        });

        remoteData.handleRefreshResponse(asSet(payload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(asSet(payload, otherPayload), subscribedPayloads);
        Assert.assertEquals(remoteData.dataStore.getPayloads(), asSet(payload, otherPayload));

        subscribedPayloads.clear();

        // Subsequent refresh response missing previously known types
        remoteData.handleRefreshResponse(asSet(otherPayload));
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
