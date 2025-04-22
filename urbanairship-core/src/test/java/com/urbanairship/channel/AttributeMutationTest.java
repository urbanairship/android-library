/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

import java.text.ParseException;

/**
 * Attributes mutation tests.
 */
public class AttributeMutationTest extends BaseTestCase {

    @Test
    public void testSetMutation() {
        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);

        JsonValue expected = JsonMap.newBuilder()
                                    .put("action", "set")
                                    .put("value", "expected_value")
                                    .put("key", "expected_key")
                                    .put("timestamp", DateUtils.createIso8601TimeStamp(100))
                                    .build()
                                    .toJsonValue();

        assertEquals(expected, mutation.toJsonValue());
    }

    @Test
    public void testRemoveMutation() {
        AttributeMutation mutation = AttributeMutation.newRemoveAttributeMutation("expected_key", 100);

        JsonValue expected = JsonMap.newBuilder()
                                    .put("action", "remove")
                                    .put("key", "expected_key")
                                    .put("timestamp", DateUtils.createIso8601TimeStamp(100))
                                    .build()
                                    .toJsonValue();

        assertEquals(expected, mutation.toJsonValue());
    }

    @Test
    public void testJsonAttributeFromJson() throws JsonException, ParseException {
        String json = "{\"action\":\"set\",\"value\":{\"name\":true,\"cool_factor\":70,\"is_cool\":true},\"key\":\"players#bob\",\"timestamp\":\"2025-04-22T18:05:45\"}";
        AttributeMutation mutation = AttributeMutation.fromJsonValue(JsonValue.parseString(json));

        JsonValue expectedBody = JsonMap.newBuilder().put("name", true).put("cool_factor", 70).put("is_cool", true).build().toJsonValue();
        AttributeMutation expected = AttributeMutation.newSetAttributeMutation("players#bob", expectedBody, DateUtils.parseIso8601("2025-04-22T18:05:45"));

        assertEquals(expected, mutation);
    }
}
