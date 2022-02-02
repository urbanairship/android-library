/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RemoteDataStoreTest extends BaseTestCase {

    private RemoteDataStore dataStore;
    private Set<RemoteDataPayload> payloads;

    @Before
    public void setUp() {
        dataStore = new RemoteDataStore(TestApplication.getApplication(), "appKey", "test");

        RemoteDataPayload payload = RemoteDataPayload.newBuilder()
                                                     .setType("type")
                                                     .setTimeStamp(123)
                                                     .setData(JsonMap.newBuilder()
                                                                     .put("foo", "bar")
                                                                     .build())
                                                     .build();

        RemoteDataPayload otherPayload = RemoteDataPayload.newBuilder()
                                                          .setType("otherType")
                                                          .setTimeStamp(234)
                                                          .setData(JsonMap.newBuilder()
                                                                          .put("baz", "boz")
                                                                          .build())
                                                          .build();
        payloads = new HashSet<>(Arrays.asList(payload, otherPayload));
    }

    @After
    public void teardown() {
        dataStore.deletePayloads();
        dataStore.close();
    }

    /**
     * Test saving payloads
     */
    @Test
    public void testSavePayloads() {
        boolean success = dataStore.savePayloads(payloads);
        Assert.assertTrue(success);
    }

    /**
     * Test getting payloads
     */
    @Test
    public void testGetPayloads() {
        dataStore.savePayloads(payloads);
        Set<RemoteDataPayload> savedPayloads = dataStore.getPayloads(Arrays.asList("type", "otherType"));
        Assert.assertNotNull(savedPayloads);
        Assert.assertEquals(payloads, savedPayloads);

        savedPayloads = dataStore.getPayloads(Arrays.asList("type"));
        Assert.assertEquals(1, savedPayloads.size());
        Assert.assertEquals("type", savedPayloads.iterator().next().getType());
    }

    /**
     * Test deleting payloads.
     */
    @Test
    public void testDeletePayloads() {
        dataStore.savePayloads(payloads);
        dataStore.deletePayloads();
        Assert.assertTrue(dataStore.getPayloads(Arrays.asList("type", "otherType")).size() == 0);
    }

}
