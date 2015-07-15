package com.urbanairship.json;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
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
    public void testToString() throws JSONException {
        String expected = "{\"some-key\":\"some-value\",\"another-key\":\"another-value\"}";

        assertEquals(expected, jsonMap.toString());
    }

    /**
     * Test toString on an empty map produces a JSON encoded String.
     */
    @Test
    public void testEmptyMapToString() {
        assertEquals("{}", new JsonMap(null).toString());
    }
}
