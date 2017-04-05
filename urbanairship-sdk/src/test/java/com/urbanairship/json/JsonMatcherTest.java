/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonMatcherTest extends BaseTestCase {

    @Test
    public void testMatcher() {
        ValueMatcher valueMatcher = ValueMatcher.newValueMatcher(JsonValue.wrap("value"));

        JsonMatcher matcher = JsonMatcher.newBuilder()
                                         .setKey("key")
                                         .setValueMatcher(valueMatcher)
                                         .build();

        JsonValue value = JsonMap.newBuilder()
                                 .put("key", "value")
                                 .build()
                                 .toJsonValue();

        assertTrue(matcher.apply(value));

        matcher = JsonMatcher.newBuilder()
                             .setKey("key")
                             .setScope("properties")
                             .setValueMatcher(valueMatcher)
                             .build();

        assertFalse(matcher.apply(value));

        value = JsonMap.newBuilder()
                       .put("properties", JsonMap.newBuilder().put("key", "value").build())
                       .build()
                       .toJsonValue();

        assertTrue(matcher.apply(value));

        matcher = JsonMatcher.newBuilder()
                             .setValueMatcher(valueMatcher)
                             .build();
        value = JsonValue.wrap("value");

        assertTrue(matcher.apply(value));
    }

    @Test
    public void testParse() throws JsonException {
        JsonValue valueJson = JsonMap.newBuilder()
                                     .put("equals", "string")
                                     .build()
                                     .toJsonValue();

        JsonValue matcherJson = JsonMap.newBuilder()
                                       .put("key", "key")
                                       .put("value", valueJson)
                                       .put("scope", new JsonList(Collections.singletonList(JsonValue.wrap("properties"))))
                                       .build()
                                       .toJsonValue();

        ValueMatcher valueMatcher = ValueMatcher.newValueMatcher(JsonValue.wrap("string"));
        JsonMatcher matcher = JsonMatcher.newBuilder()
                                         .setKey("key")
                                         .setScope("properties")
                                         .setValueMatcher(valueMatcher)
                                         .build();

        assertEquals(matcher, JsonMatcher.parse(matcherJson));
    }

    /**
     * Test parsing an empty JsonMap throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseEmptyMap() throws JsonException {
        JsonMatcher.parse(JsonMap.EMPTY_MAP.toJsonValue());
    }

    /**
     * Test parsing an invalid JsonValue throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseInvalidJson() throws JsonException {
        JsonMatcher.parse(JsonValue.wrap("not valid"));
    }

    /**
     * Test parsing an invalid JsonValue throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseJsonMissingValueMatcher() throws JsonException {
        JsonValue json = JsonMap.newBuilder()
                                .put("key", "cool")
                                .build()
                                .toJsonValue();

        JsonMatcher.parse(json);
    }
}
