/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeferredTest {

    @Test
    public void testFromJson() throws JsonException, MalformedURLException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "https://neat.com")
                                     .put("retry_on_timeout", false)
                                     .put("type", "in_app_message")
                                     .build()
                                     .toJsonValue();

        Deferred deferred = Deferred.fromJson(jsonValue);
        assertEquals(new URL("https://neat.com"), deferred.getUrl());
        assertFalse(deferred.isRetriableOnTimeout());
        assertEquals("in_app_message", deferred.getType());
    }

    @Test
    public void testFromJsonDefaultRetry() throws JsonException, MalformedURLException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "https://neat.com")
                                     .build()
                                     .toJsonValue();

        Deferred deferred = Deferred.fromJson(jsonValue);
        assertEquals(new URL("https://neat.com"), deferred.getUrl());
        assertTrue(deferred.isRetriableOnTimeout());
    }

    @Test(expected = JsonException.class)
    public void testFromJsonMissingURL() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "bloop")
                                     .build()
                                     .toJsonValue();

        Deferred.fromJson(jsonValue);
    }

    @Test(expected = JsonException.class)
    public void testFromJsonInvalidURL() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("retry_on_timeout", false)
                                     .build()
                                     .toJsonValue();

        Deferred.fromJson(jsonValue);
    }

    @Test
    public void testToJSON() throws MalformedURLException {
        JsonValue expected = JsonMap.newBuilder()
                                    .put("url", "https://neat.com")
                                    .put("retry_on_timeout", false)
                                    .build()
                                    .toJsonValue();

        JsonValue toJson = new Deferred(new URL("https://neat.com"), false, null).toJsonValue();

        assertEquals(expected, toJson);
    }
}
