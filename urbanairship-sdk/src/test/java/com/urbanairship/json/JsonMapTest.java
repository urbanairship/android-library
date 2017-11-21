/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class JsonMapTest extends BaseTestCase {

    private JsonMap jsonMap;

    @Before
    public void setUp() throws JsonException {
        Map<String, Object> map = new HashMap<>();
        map.put("null-key", null);
        map.put("some-key", "some-value");
        map.put("another-key", "another-value");

        jsonMap = JsonValue.wrap(map).getMap();
        assertNotNull(jsonMap);
    }

    /**
     * Test creating a new JsonMap with a null map.
     */
    @Test
    public void testCreateNull() throws JsonException, JSONException {
        JsonMap emptyMap = new JsonMap(null);
        assertEquals(0, emptyMap.size());
        assertTrue(emptyMap.isEmpty());
        assertNull(emptyMap.get("Not in map"));
    }

    /**
     * Test getting an optional value returns a null JsonValue instead of null.
     */
    @Test
    public void testOpt() throws JSONException, JsonException {
        // Verify it gets values that are available
        assertEquals("some-value", jsonMap.opt("some-key").getString());

        // Verify it returns JsonValue.NULL instead of null for unavailable values
        assertTrue(jsonMap.opt("Not in map").isNull());
    }

    /**
     * Test toString produces a JSON encoded String.
     */
    @Test
    public void testToString() throws JsonException {
        JsonValue parsedValue = JsonValue.parseString(jsonMap.toString());
        assertEquals(parsedValue.getMap(), jsonMap);
    }

    /**
     * Test toString on an empty map produces a JSON encoded String.
     */
    @Test
    public void testEmptyMapToString() {
        assertEquals("{}", new JsonMap(null).toString());
    }

    @Test
    public void testMapBuilder() {
        List list = Arrays.asList("String", 1.2, false, 1, 'c');

        jsonMap = JsonMap.newBuilder()
                         .putAll(jsonMap)
                         .put("boolean", true)
                         .put("int", 1)
                         .put("char", 'c')
                         .put("String", "String")
                         .put("Empty String", "")
                         .put("list", JsonValue.wrapOpt(list))
                         .build();

        assertEquals("", jsonMap.get("Empty String").getString());
        assertEquals("some-value", jsonMap.get("some-key").getString());
        assertEquals("another-value", jsonMap.get("another-key").getString());
        assertEquals(true, jsonMap.get("boolean").getBoolean(false));
        assertEquals(1, jsonMap.get("int").getInt(2));
        assertEquals("c", jsonMap.get("char").getString());
        assertEquals("String", jsonMap.get("String").getString());

        assertEquals("String", jsonMap.get("list").getList().getList().get(0).getString());
        assertEquals(1.2, jsonMap.get("list").getList().getList().get(1).getDouble(2.2));
        assertEquals(false, jsonMap.get("list").getList().getList().get(2).getBoolean(true));
        assertEquals(1, jsonMap.get("list").getList().getList().get(3).getInt(2));
        assertEquals("c", jsonMap.get("list").getList().getList().get(4).getString());
    }
}
