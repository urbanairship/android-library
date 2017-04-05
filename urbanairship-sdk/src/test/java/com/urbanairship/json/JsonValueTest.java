/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import android.os.Parcel;

import com.urbanairship.BaseTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;


public class JsonValueTest extends BaseTestCase {

    Map<String, Object> primitiveMap;
    List<Object> primitiveList;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        primitiveList = Arrays.asList(new Object[] { "String", 1.2, false, 1, 'c', (byte) 2, (short) 3 });

        primitiveMap = new HashMap<>();
        primitiveMap.put("double", 1.2);
        primitiveMap.put("boolean", true);
        primitiveMap.put("int", 1);
        primitiveMap.put("char", 'c');
        primitiveMap.put("byte", (byte) 2);
        primitiveMap.put("short", (short) 3);
        primitiveMap.put("String", "String");
    }

    /**
     * Test wrapping a JsonSerializable object returns the objects JsonValue.
     */
    @Test
    public void testWrapJsonSerializable() throws JsonException, JSONException {
        final JsonValue serializableValue = JsonValue.wrap("some value");
        Object jsonSerializable = new JsonSerializable() {
            @Override
            public JsonValue toJsonValue() {
                return serializableValue;
            }
        };

        JsonValue jsonValue = JsonValue.wrap(jsonSerializable);
        assertEquals(serializableValue, jsonValue);
    }

    /**
     * Test wrapping a JsonSerializable object returns JsonValue.NULL if the object returns
     * null for the JsonValue.
     */
    @Test
    public void testWrapJsonSerializableNull() throws JsonException, JSONException {
        Object jsonSerializable = new JsonSerializable() {
            @Override
            public JsonValue toJsonValue() {
                return null;
            }
        };

        JsonValue jsonValue = JsonValue.wrap(jsonSerializable);
        assertEquals(JsonValue.NULL, jsonValue);
        assertNull(jsonValue.getValue());
    }

    /**
     * Test wrapping a JSONObject.
     */
    @Test
    public void testWrapJSONObject() throws JsonException, JSONException {
        JSONObject jsonObject = new JSONObject(primitiveMap);
        jsonObject.put("map", new JSONObject(primitiveMap));
        jsonObject.put("collection", new JSONArray(primitiveList));

        JsonValue jsonValue = JsonValue.wrap(jsonObject);
        assertTrue(jsonValue.getValue() instanceof JsonMap);

        JsonMap jsonMap = jsonValue.getMap();

        // Validate all the values in the map
        for (Map.Entry<String, JsonValue> entry : jsonMap.entrySet()) {
            // Wrap the value individual so all the values will be coerced the same
            JsonValue value = JsonValue.wrap(jsonObject.get(entry.getKey()));
            assertEquals(value, entry.getValue());
        }

        assertEquals(jsonObject.length(), jsonMap.size());
    }

    /**
     * Test wrapping a JSONArray.
     */
    @Test
    public void testWrapJSONArray() throws JSONException, JsonException {
        JSONArray jsonArray = new JSONArray(primitiveList);
        jsonArray.put(new JSONObject(primitiveMap));
        jsonArray.put(new JSONArray(primitiveList));

        JsonValue jsonValue = JsonValue.wrap(jsonArray);
        assertTrue(jsonValue.getValue() instanceof JsonList);

        JsonList jsonList = jsonValue.getList();

        // Validate all the values in the list
        for (int i = 0; i < jsonList.size(); i++) {
            // Wrap the value individual so all the values will be coerced the same
            JsonValue value = JsonValue.wrap(jsonArray.get(i));
            assertEquals(value, jsonList.get(i));
        }

        assertEquals(jsonArray.length(), jsonList.size());
    }

    /**
     * Test wrapping a map.
     */
    @Test
    public void testWrapMap() throws JSONException, JsonException {
        Map<String, Object> map = new HashMap<>(primitiveMap);
        map.put("map", primitiveMap);
        map.put("collection", primitiveList);

        JsonValue jsonValue = JsonValue.wrap(map);
        assertTrue(jsonValue.getValue() instanceof JsonMap);

        JsonMap jsonMap = jsonValue.getMap();
        assertNotNull(jsonMap);

        // Validate all the values in the map
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            // Wrap the value individual so all the values will be coerced the same
            JsonValue value = JsonValue.wrap(entry.getValue());
            assertEquals(value, jsonMap.get(entry.getKey()));
        }

        assertEquals(map.size(), jsonMap.size());
    }


    /**
     * Test wrapping a list.
     */
    @Test
    public void testWrapList() throws JsonException {
        List<Object> list = new ArrayList<>(primitiveList);

        // Add a map and the list as child items
        list.add(primitiveMap);
        list.add(primitiveList);

        JsonValue jsonValue = JsonValue.wrap(list);
        assertTrue(jsonValue.getValue() instanceof JsonList);

        JsonList jsonList = jsonValue.getList();

        // Validate all the values are in the list properly
        for (int i = 0; i < jsonList.size(); i++) {
            // Wrap the value individual so all the values will be coerced the same
            JsonValue wrappedValue = JsonValue.wrap(list.get(i));
            assertEquals(wrappedValue, jsonList.get(i));
        }

        assertEquals(list.size(), jsonList.size());
    }

    /**
     * Test wrapping an array.
     */
    @Test
    public void testWrapArray() throws JsonException {
        List<Object> list = new ArrayList<>(primitiveList);

        // Add a map and the list as child items
        list.add(primitiveMap);
        list.add(primitiveList);

        Object[] array = list.toArray(new Object[list.size()]);

        JsonValue jsonValue = JsonValue.wrap(list);
        assertTrue(jsonValue.getValue() instanceof JsonList);

        JsonList jsonList = jsonValue.getList();
        assertNotNull(jsonList);

        // Validate all the values are in the list properly
        for (int i = 0; i < array.length; i++) {
            // Wrap the value individual so all the values will be coerced the same
            JsonValue wrappedValue = JsonValue.wrap(array[i]);
            assertEquals(wrappedValue, jsonList.get(i));
        }

        assertEquals(array.length, jsonList.size());
    }

    /**
     * Test wrapping integers.
     */
    @Test
    public void testWrapInteger() throws JsonException {
        // bytes and shorts are converted to Integer
        assertEquals(1, JsonValue.wrap((byte) 1).getInt(0));
        assertEquals(1, JsonValue.wrap((short) 1).getInt(0));
        assertEquals(1, JsonValue.wrap(1).getInt(0));

        assertTrue(JsonValue.wrap(1).getValue() instanceof Integer);
    }

    /**
     * Test wrapping longs.
     */
    @Test
    public void testWrapLong() throws JsonException {
        assertEquals(1l, JsonValue.wrap(1l).getLong(0));
        assertTrue(JsonValue.wrap(1l).getValue() instanceof Long);
    }

    /**
     * Test wrapping doubles.
     */
    @Test
    public void testWrapDouble() throws JsonException {
        // floats are converted to doubles
        assertEquals(1.0d, JsonValue.wrap(1.0f).getDouble(0));
        assertTrue(JsonValue.wrap(1.0f).getValue() instanceof Double);

        assertEquals(1.0d, JsonValue.wrap(1.0d).getDouble(0));
        assertTrue(JsonValue.wrap(1.0d).getValue() instanceof Double);
    }

    /**
     * Test wrapping booleans.
     */
    @Test
    public void testWrapBoolean() throws JsonException {
        assertTrue(JsonValue.wrap(true).getBoolean(false));
        assertTrue(JsonValue.wrap(true).getValue() instanceof Boolean);

        assertFalse(JsonValue.wrap(false).getBoolean(true));
        assertTrue(JsonValue.wrap(false).getValue() instanceof Boolean);
    }

    /**
     * Test wrapping strings.
     */
    @Test
    public void testWrapString() throws JsonException, MalformedURLException, URISyntaxException {
        assertEquals("Hello", JsonValue.wrap("Hello").getString());
        assertTrue(JsonValue.wrap("Hello").getValue() instanceof String);

        assertEquals("c", JsonValue.wrap('c').getString());
        assertTrue(JsonValue.wrap('c').getValue() instanceof String);
    }

    /**
     * Test wrapping null.
     */
    @Test
    public void testWrapNull() throws JsonException {
        assertTrue(JsonValue.wrap((Object) null).isNull());
    }

    /**
     * Test JsonValue toString produces valid JSON output.
     */
    @Test
    public void testToString() throws JsonException, JSONException {
        // Primitives
        assertEquals("\"Hello\"", JsonValue.wrap("Hello").toString());
        assertEquals("1", JsonValue.wrap(1).toString());
        assertEquals(String.valueOf(Long.MAX_VALUE), JsonValue.wrap(Long.MAX_VALUE).toString());
        assertEquals("1.2", JsonValue.wrap(1.2).toString());
        assertEquals("true", JsonValue.wrap(true).toString());
        assertEquals("false", JsonValue.wrap(false).toString());
        assertEquals("null", JsonValue.NULL.toString());

        // Map
        Map<String, Object> map = new HashMap<>(primitiveMap);
        map.put("map", primitiveMap);
        map.put("collection", primitiveList);

        JsonValue expected = JsonValue.parseString("{\"short\":3,\"char\":\"c\",\"byte\":2,\"int\":1,\"String\":\"String\"," +
                "\"map\":{\"short\":3,\"byte\":2,\"char\":\"c\",\"String\":\"String\",\"int\":1," +
                "\"boolean\":true,\"double\":1.2},\"boolean\":true,\"collection\":[\"String\",1.2," +
                "false,1,\"c\",2,3],\"double\":1.2}");

        assertEquals(expected.getMap(), JsonValue.wrap(map));

        // List
        List<Object> list = new ArrayList<>(primitiveList);
        list.add(primitiveList);

        expected = JsonValue.parseString("[\"String\",1.2,false,1,\"c\",2,3,[\"String\",1.2,false,1,\"c\",2,3]]");

        assertEquals(expected.getList(), JsonValue.wrap(list).getList());
    }


    /**
     * Test parsing a valid JSON String produces the equivalent JsonValue.
     */
    @Test
    public void testParseString() throws JsonException, JSONException {
        assertEquals(JsonValue.wrap("Hello"), JsonValue.parseString("\"Hello\""));
        assertEquals(JsonValue.wrap(1), JsonValue.parseString("1"));
        assertEquals(JsonValue.wrap(true), JsonValue.parseString("true"));
        assertEquals(JsonValue.wrap(false), JsonValue.parseString("false"));
        assertEquals(JsonValue.wrap(Long.MAX_VALUE), JsonValue.parseString(String.valueOf(Long.MAX_VALUE)));
        assertEquals(JsonValue.wrap(1.4), JsonValue.parseString(String.valueOf(1.4)));
        assertEquals(JsonValue.NULL, JsonValue.parseString("null"));
        assertEquals(JsonValue.NULL, JsonValue.parseString(null));

        // Test empty map
        assertEquals(new JsonMap(null), JsonValue.parseString("{}").getMap());

        // Test empty list
        assertEquals(new JsonList(null), JsonValue.parseString("[]").getList());

        // Map
        JSONObject json = new JSONObject(primitiveMap);
        json.put("map", new JSONObject(primitiveMap));
        json.put("collection", new JSONArray(primitiveList));
        assertEquals(JsonValue.wrap(json), JsonValue.parseString(json.toString()));

        // List
        JSONArray jsonArray = new JSONArray(primitiveList);
        assertEquals(JsonValue.wrap(jsonArray), JsonValue.parseString(jsonArray.toString()));
    }


    /**
     * Test trying to wrap Double.NaN throws an exception.
     */
    @Test
    public void testDoubleNAN() throws JsonException {
        assertEquals(JsonValue.NULL, JsonValue.wrap(Double.NaN));
    }

    /**
     * Test trying to wrap Double.NEGATIVE_INFINITY throws an exception.
     */
    @Test
    public void testDoubleNegativeInfinity() throws JsonException {
        assertEquals(JsonValue.NULL, JsonValue.wrap(Double.NEGATIVE_INFINITY));
    }

    /**
     * Test trying to wrap Double.POSITIVE_INFINITY throws an exception.
     */
    @Test
    public void testDoublePositiveInfinity() throws JsonException {
        assertEquals(JsonValue.NULL, JsonValue.wrap(Double.POSITIVE_INFINITY));
    }


    /**
     * Test saving and reading a JsonValue from a parcel.
     */
    @Test
    public void testParcelable() throws JsonException {
        JsonValue jsonValue = JsonValue.wrap(primitiveMap);

        // Write the push message to a parcel
        Parcel parcel = Parcel.obtain();
        jsonValue.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the message from the parcel
        JsonValue fromParcel = JsonValue.CREATOR.createFromParcel(parcel);

        // Validate the data
        assertEquals(jsonValue, fromParcel);
    }

    /**
     * Test isNull is true for null values.
     */
    @Test
    public void testIsNull() throws JsonException {
        assertTrue(JsonValue.NULL.isNull());
        assertTrue(JsonValue.wrap((Object) null).isNull());
    }

    /**
     * Test isString is true for String values.
     */
    @Test
    public void testIsString() {
        assertTrue(JsonValue.wrap('c').isString());
        assertTrue(JsonValue.wrap("hi").isString());
    }

    /**
     * Test isInteger is true only for int values.
     */
    @Test
    public void testIsInteger() throws JsonException {
        assertTrue(JsonValue.wrap(1).isInteger());

        assertFalse(JsonValue.wrap(1l).isInteger());
        assertFalse(JsonValue.wrap(1.0d).isInteger());
        assertFalse(JsonValue.wrap(1.0f).isInteger());
    }

    /**
     * Test isLong is true only for longs.
     */
    @Test
    public void testIsLong() throws JsonException {
        assertTrue(JsonValue.wrap(1l).isLong());

        assertFalse(JsonValue.wrap(1).isLong());
        assertFalse(JsonValue.wrap(1.0d).isLong());
        assertFalse(JsonValue.wrap(1.0f).isLong());
    }

    /**
     * Test isDouble is true for floats and doubles.
     */
    @Test
    public void testIsDouble() throws JsonException {
        assertTrue(JsonValue.wrap(1d).isDouble());
        assertTrue(JsonValue.wrap(1f).isDouble());

        assertFalse(JsonValue.wrap(1l).isDouble());
        assertFalse(JsonValue.wrap(1).isDouble());
    }

    /**
     * Test isNumber is true for any number types.
     */
    @Test
    public void testIsNumber() throws JsonException {
        assertTrue(JsonValue.wrap(1d).isNumber());
        assertTrue(JsonValue.wrap(1f).isNumber());
        assertTrue(JsonValue.wrap(1).isNumber());
        assertTrue(JsonValue.wrap(1l).isNumber());
    }

    /**
     * Test isBoolean is true for any boolean values.
     */
    @Test
    public void testIsBoolean() {
        assertTrue(JsonValue.wrap(true).isBoolean());
        assertTrue(JsonValue.wrap(false).isBoolean());
    }

    /**
     * Test isJsonMap is true for map values.
     */
    @Test
    public void testIsJsonMap() throws JsonException {
        assertTrue(JsonValue.wrap(new HashMap<String, String>()).isJsonMap());
    }

    /**
     * Test isJsonList is true for list values.
     */
    @Test
    public void testIsJsonList() throws JsonException {
        assertTrue(JsonValue.wrap(new ArrayList<String>()).isJsonList());
    }

    /**
     * Tests the double handling in {@link JsonValue#equals(Object)}.
     */
    @Test
    public void testNumberEquals() {
        JsonValue doubleValue = JsonValue.wrap(1.5);
        JsonValue intValue = JsonValue.wrap(1);
        assertFalse(intValue.equals(doubleValue));

        doubleValue = JsonValue.wrap(1.0);
        assertTrue(intValue.equals(doubleValue));
    }
}
