/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PreferenceDataStoreTest extends BaseTestCase {

    private Context context;
    private PreferenceDataStore testPrefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application.getApplicationContext();
        testPrefs = new PreferenceDataStore(context);
    }

    /**
     * Test saving string values.
     */
    @Test
    public void testString() {
        testPrefs.put("value", "oh hi");
        assertEquals("oh hi", testPrefs.getString("value", "oh hi"));

        testPrefs.put("value", (String) null);
        assertNull(testPrefs.getString("value", null));
    }

    /**
     * Test saving longs.
     */
    @Test
    public void testLong() {
        testPrefs.put("value", 123l);
        assertEquals(123, testPrefs.getLong("value", -1));
    }

    /**
     * Test saving ints.
     */
    @Test
    public void testInt() {
        testPrefs.put("value", 123);
        assertEquals(123, testPrefs.getInt("value", -1));
    }

    /**
     * Test saving booleans.
     */
    @Test
    public void testBoolean() {
        testPrefs.put("value", true);
        assertTrue(testPrefs.getBoolean("value", false));

        testPrefs.put("value", false);
        assertFalse(testPrefs.getBoolean("value", true));
    }

    /**
     * Test saving json values.
     */
    @Test
    public void testJsonValue() throws JsonException {
        Map map = new HashMap();
        map.put("string", "string");
        map.put("int", 123);
        map.put("double", 123.123);

        JsonValue value = JsonValue.wrap(map);

        testPrefs.put("value", value);
        assertEquals(value, testPrefs.getJsonValue("value"));

        testPrefs.put("value", (JsonValue) null);
        assertTrue(testPrefs.getJsonValue("value").isNull());
    }

    /**
     * Test saving json serializable values.
     */
    @Test
    public void testJsonSerializable() throws JsonException {
        Map map = new HashMap();
        map.put("string", "string");
        map.put("int", 123);
        map.put("double", 123.123);

        final JsonValue value = JsonValue.wrap(map);

        JsonSerializable testObject = new JsonSerializable() {
            @Override
            public JsonValue toJsonValue() {
                return value;
            }
        };

        testPrefs.put("value", testObject);
        assertEquals(value, testPrefs.getJsonValue("value"));

        testPrefs.put("value", (JsonSerializable) null);
        assertTrue(testPrefs.getJsonValue("value").isNull());
    }

    /**
     * Test saving json serializable when toJson returns null.
     */
    @Test
    public void testJsonSerializableNullJsonValue() throws JsonException {
        JsonSerializable testObject = new JsonSerializable() {
            @Override
            public JsonValue toJsonValue() {
                return null;
            }
        };

        testPrefs.put("value", testObject);
        assertTrue(testPrefs.getJsonValue("value").isNull());
    }
}
