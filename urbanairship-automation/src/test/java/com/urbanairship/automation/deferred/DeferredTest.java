/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import android.net.Uri;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DeferredTest {

    @Test
    public void testFromJson() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "https://neat.com")
                                     .put("retry_on_timeout", false)
                                     .put("type", "in_app_message")
                                     .build()
                                     .toJsonValue();

        Deferred deferred = Deferred.fromJson(jsonValue);
        assertEquals(Uri.parse("https://neat.com"), deferred.getUrl());
        assertFalse(deferred.isRetriableOnTimeout());
        assertEquals("in_app_message", deferred.getType());
    }

    @Test
    public void testFromJsonDefaultRetry() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
                                     .put("url", "https://neat.com")
                                     .build()
                                     .toJsonValue();

        Deferred deferred = Deferred.fromJson(jsonValue);
        assertEquals(Uri.parse("https://neat.com"), deferred.getUrl());
        assertTrue(deferred.isRetriableOnTimeout());
    }

    @Test(expected = JsonException.class)
    public void testFromJsonMissingURL() throws JsonException {
        JsonValue jsonValue = JsonMap.newBuilder()
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
    public void testToJSON() {
        JsonValue expected = JsonMap.newBuilder()
                                    .put("url", "https://neat.com")
                                    .put("retry_on_timeout", false)
                                    .build()
                                    .toJsonValue();

        JsonValue toJson = new Deferred(Uri.parse("https://neat.com"), false, null).toJsonValue();

        assertEquals(expected, toJson);
    }
}
