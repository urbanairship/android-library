/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;

public class RemoteDataStoreTest extends BaseTestCase {

    private RemoteDataStore dataStore;
    private List<RemoteDataPayload> payloads;

    @Before
    public void setUp() {
        dataStore = new RemoteDataStore(RuntimeEnvironment.application, "appKey", "test");

        RemoteDataPayload payload = new RemoteDataPayload("type", 123, JsonMap.newBuilder().put("foo", "bar").build());
        RemoteDataPayload otherPayload = new RemoteDataPayload("otherType", 234, JsonMap.newBuilder().put("baz", "boz").build());
        payloads = Arrays.asList(payload, otherPayload);
    }

    @After
    public void teardown() {
        dataStore.deletePayloads();
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
        List<RemoteDataPayload> savedPayloads = dataStore.getPayloads();
        Assert.assertNotNull(savedPayloads);
        Assert.assertEquals(payloads, savedPayloads);
    }

    /**
     * Test deleting payloads.
     */
    @Test
    public void testDeletePayloads() {
        dataStore.savePayloads(payloads);
        dataStore.deletePayloads();
        Assert.assertTrue(dataStore.getPayloads().size() == 0);
    }
}
