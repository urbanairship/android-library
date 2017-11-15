/* Copyright 2017 Urban Airship and Contributors */

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
import java.util.List;

import static org.mockito.Mockito.mock;
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
        remoteData.stop();
        activityMonitor.unregister();
    }

    /**
     * Test that a transition to foreground results in a refresh.
     */
    @Test
    public void testForegroundTransition() {
        remoteData.init();
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
     * Test that an empty cache will result in empty model objects in the initial callback.
     */
    @Test
    public void testPayloadsForTypeWithEmptyCache() {
        remoteData.start();
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

        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
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
        remoteData.start();
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

        remoteData.handleRefreshResponse(Arrays.asList(otherPayload));

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
        remoteData.start();

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

        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
        runLooperTasks();

        // We should get a second callback with the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), payload);

        subscribedPayloads.clear();

        // Replaying the response should not result in another callback because the data hasn't changed
        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
        runLooperTasks();
        Assert.assertEquals(subscribedPayloads.size(), 0);

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = new RemoteDataPayload(payload.getType(), payload.getTimestamp() + 100000, payload.getData());
        remoteData.handleRefreshResponse(Arrays.asList(freshPayload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads.size(), 1);
        Assert.assertEquals(subscribedPayloads.get(0), freshPayload);
    }

    /**
     * Test that an empty cache will result in empty model objects in the initial callback.
     */
    @Test
    public void testPayloadsForTypesWithEmptyCache() {
        remoteData.start();
        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes("type", "otherType");

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

        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
        runLooperTasks();

        // The second callback should be the refreshed data
        Assert.assertEquals(subscribedPayloads.size(), 1);
        collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection.size(), 2);
        Assert.assertEquals(collection, Arrays.asList(payload, otherPayload));
    }

    /**
     * Test that a type missing in the response will result in empty model objects in the callback.
     */
    @Test
    public void testPayloadsForTypesWithMissingTypeInResponse() throws Exception {
        remoteData.start();
        remoteData.dataStore.savePayload(payload);
        remoteData.dataStore.savePayload(otherPayload);

        Assert.assertEquals(remoteData.dataStore.getPayloads().size(), 2);

        runLooperTasks();

        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes("type", "otherType");

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
        Assert.assertEquals(collection, Arrays.asList(payload, otherPayload));

        subscribedPayloads.clear();

        RemoteDataPayload freshOtherPayload = new RemoteDataPayload(otherPayload.getType(), otherPayload.getTimestamp() + 10000, otherPayload.getData());

        remoteData.handleRefreshResponse(Arrays.asList(freshOtherPayload));
        runLooperTasks();

        // The second callback should have an empty placeholder for the first payload
        Assert.assertEquals(subscribedPayloads.size(), 1);
        collection = subscribedPayloads.get(0);
        Assert.assertEquals(collection, Arrays.asList(emptyPayload, freshOtherPayload));
    }

    /**
     * Test that a multiple types will only produce a callback if at least one of the payloads has changed
     */
    @Test
    public void testPayloadsForTypesDistinctness() {
        remoteData.start();

        final List<Collection<RemoteDataPayload>> subscribedPayloads = new ArrayList<>();

        Observable<Collection<RemoteDataPayload>> payloadsObservable = remoteData.payloadsForTypes("type", "otherType");
        payloadsObservable.subscribe(new Subscriber<Collection<RemoteDataPayload>>(){
            @Override
            public void onNext(Collection<RemoteDataPayload> value) {
                subscribedPayloads.add(value);
            }
        });

        runLooperTasks();

        // Clear the first callback
        subscribedPayloads.clear();

        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads, Arrays.asList(Arrays.asList(payload, otherPayload)));

        // Replaying the response should not result in another callback because the timestamp hasn't changed
        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
        runLooperTasks();
        Assert.assertEquals(subscribedPayloads, Arrays.asList(Arrays.asList(payload, otherPayload)));

        subscribedPayloads.clear();

        // Sending a fresh payload with an updated timestamp should result in a new callback
        RemoteDataPayload freshPayload = new RemoteDataPayload(payload.getType(), payload.getTimestamp() + 100000, payload.getData());
        remoteData.handleRefreshResponse(Arrays.asList(freshPayload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(subscribedPayloads, Arrays.asList(Arrays.asList(freshPayload, otherPayload)));
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
        remoteData.start();

        final List<RemoteDataPayload> subscribedPayloads = new ArrayList<>();

        remoteData.payloadUpdates.subscribe(new Subscriber<List<RemoteDataPayload>>() {
            @Override
            public void onNext(List<RemoteDataPayload> payloads) {
                for (RemoteDataPayload payload : payloads) {
                    subscribedPayloads.add(payload);
                }
            }
        });

        remoteData.handleRefreshResponse(Arrays.asList(payload, otherPayload));
        runLooperTasks();

        Assert.assertEquals(Arrays.asList(payload, otherPayload), subscribedPayloads);
        Assert.assertEquals(remoteData.dataStore.getPayloads(), Arrays.asList(payload, otherPayload));

        subscribedPayloads.clear();

        // Subsequent refresh response missing previously known types
        remoteData.handleRefreshResponse(Arrays.asList(otherPayload));
        runLooperTasks();

        Assert.assertEquals(Arrays.asList(otherPayload), subscribedPayloads);

        // "Deleted" payload types should not persist in the cache
        Assert.assertEquals(remoteData.dataStore.getPayloads(), Arrays.asList(otherPayload));
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
}
