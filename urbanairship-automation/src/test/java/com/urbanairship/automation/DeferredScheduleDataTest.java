/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeferredScheduleDataTest {

    @Test
    public void testFromJson() throws JsonException, MalformedURLException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "https://neat.com")
                                     .put("retry_on_timeout", false)
                                     .build()
                                     .toJsonValue();

        DeferredScheduleData deferredScheduleData = DeferredScheduleData.fromJson(jsonValue);
        assertEquals(new URL("https://neat.com"), deferredScheduleData.getUrl());
        assertFalse(deferredScheduleData.isRetriableOnTimeout());
    }

    @Test
    public void testFromJsonDefaultRetry() throws JsonException, MalformedURLException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "https://neat.com")
                                     .build()
                                     .toJsonValue();

        DeferredScheduleData deferredScheduleData = DeferredScheduleData.fromJson(jsonValue);
        assertEquals(new URL("https://neat.com"), deferredScheduleData.getUrl());
        assertTrue(deferredScheduleData.isRetriableOnTimeout());
    }

    @Test(expected = JsonException.class)
    public void testFromJsonMissingURL() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "bloop")
                                     .build()
                                     .toJsonValue();

        DeferredScheduleData.fromJson(jsonValue);
    }

    @Test(expected = JsonException.class)
    public void testFromJsonInvalidURL() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("retry_on_timeout", false)
                                     .build()
                                     .toJsonValue();

        DeferredScheduleData.fromJson(jsonValue);
    }

    @Test
    public void testToJSON() throws MalformedURLException {
        JsonValue expected = JsonMap.newBuilder()
                                    .put("url", "https://neat.com")
                                    .put("retry_on_timeout", false)
                                    .build()
                                    .toJsonValue();

        JsonValue toJson = new DeferredScheduleData(new URL("https://neat.com"), false).toJsonValue();

        assertEquals(expected, toJson);
    }
}
