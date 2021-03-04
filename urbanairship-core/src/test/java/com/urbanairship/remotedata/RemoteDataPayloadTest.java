/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

public class RemoteDataPayloadTest extends BaseTestCase {

    private JsonList payloads;
    private JsonValue payload;
    private String timestamp;
    private JsonMap data;
    private JsonMap metadata;

    @Before
    public void setup() {
        timestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());
        data = JsonMap.newBuilder().put("foo", "bar").build();
        payload = JsonMap.newBuilder().put("type", "test").put("timestamp", timestamp).put("data", data).build().toJsonValue();
        payloads = new JsonList(Collections.singletonList(payload));
        metadata = JsonMap.newBuilder().put("foo", "bar").build();
    }

    @Test
    public void testParsePayload() throws Exception {
        RemoteDataPayload parsedPayload = RemoteDataPayload.parsePayload(payload, metadata);
        verifyPayload(parsedPayload);
    }

    @Test
    public void testParsePayloads() {
        Set<RemoteDataPayload> parsedPayloads = RemoteDataPayload.parsePayloads(payloads, metadata);
        Assert.assertEquals("Parsed payloads should have a size of one", parsedPayloads.size(), 1);
        for (RemoteDataPayload parsedPayload : parsedPayloads) {
            verifyPayload(parsedPayload);
        }
    }

    private void verifyPayload(RemoteDataPayload parsedPayload) {
        Assert.assertEquals("Payload should have type 'test'", parsedPayload.getType(), "test");
        Assert.assertEquals("Payload should have timestamp: " + timestamp, DateUtils.createIso8601TimeStamp(parsedPayload.getTimestamp()), timestamp);
        Assert.assertEquals("Payload should have data: " + data, parsedPayload.getData(), data);
        Assert.assertEquals("Payload should have metadata: " + metadata, parsedPayload.getData(), metadata);
    }

}
