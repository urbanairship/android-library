package com.urbanairship.json;

import android.os.Parcel;

import com.urbanairship.RobolectricGradleTestRunner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

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
import static junit.framework.Assert.assertTrue;


@RunWith(RobolectricGradleTestRunner.class)
public class JsonValueTest {

    Map<String, Object> primitiveMap;
    List<Object> primitiveList;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        primitiveList = Arrays.asList(new Object[] {"String", 1.2, false, 1, 'c', (byte) 2, (short) 3});

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
    }

    /**
     * Test wrapping a JSONObject.
     */
    @Test
    public void testWrapJSONObject() throws JsonException, JSONException {
        JSONObject jsonObject = new JSONObject(primitiveMap);
        jsonObject.put("map", new JSONObject(primitiveMap));
        jsonObject.put("collection", new JSONArray(primitiveList));

        JsonMap jsonMap = JsonValue.wrap(jsonObject).getMap();

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


        JsonList jsonList = JsonValue.wrap(jsonArray).getList();

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
        map.put("collection",primitiveList);

        JsonMap jsonMap = JsonValue.wrap(map).getMap();
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

        JsonList jsonList = JsonValue.wrap(list).getList();

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

        JsonList jsonList = JsonValue.wrap(list).getList();
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
        assertEquals(1, JsonValue.wrap((byte)1).getInt(0));
        assertEquals(1, JsonValue.wrap((short)1).getInt(0));

        assertEquals(1, JsonValue.wrap(1).getInt(0));
    }

    /**
     * Test wrapping longs.
     */
    @Test
    public void testWrapLong() throws JsonException {
        assertEquals(1l, JsonValue.wrap(1l).getLong(0));
    }

    /**
     * Test wrapping doubles.
     */
    @Test
    public void testWrapDouble() throws JsonException {
        // floats are converted to doubles
        assertEquals(1.0d, JsonValue.wrap(1.0f).getDouble(0));

        assertEquals(1.0d, JsonValue.wrap(1.0d).getDouble(0));
    }

    /**
     * Test wrapping booleans.
     */
    @Test
    public void testWrapBoolean() throws JsonException {
        assertTrue(JsonValue.wrap(true).getBoolean(false));
        assertFalse(JsonValue.wrap(false).getBoolean(true));
    }

    /**
     * Test wrapping strings.
     */
    @Test
    public void testWrapString() throws JsonException, MalformedURLException, URISyntaxException {
        assertEquals("Hello", JsonValue.wrap("Hello").getString());
        assertEquals("c", JsonValue.wrap('c').getString());
    }

    /**
     * Test wrapping null.
     */
    @Test
    public void testWrapNull() throws JsonException {
        assertTrue(JsonValue.wrap(null).isNull());
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

        String expected = "{\"short\":3,\"char\":\"c\",\"byte\":2,\"int\":1,\"String\":\"String\"," +
                "\"map\":{\"short\":3,\"byte\":2,\"char\":\"c\",\"String\":\"String\",\"int\":1," +
                "\"boolean\":true,\"double\":1.2},\"boolean\":true,\"collection\":[\"String\",1.2," +
                "false,1,\"c\",2,3],\"double\":1.2}";

        assertEquals(expected, JsonValue.wrap(map).toString());

        // List
        List<Object> list = new ArrayList<>(primitiveList);
        list.add(primitiveList);

        expected = "[\"String\",1.2,false,1,\"c\",2,3,[\"String\",1.2,false,1,\"c\",2,3]]";

        assertEquals(expected, JsonValue.wrap(list).toString());
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
        // Expect the exception
        exception.expect(JsonException.class);
        exception.expectMessage("Invalid Double value: " + Double.NaN);

        JsonValue.wrap(Double.NaN);
    }

    /**
     * Test trying to wrap Double.NEGATIVE_INFINITY throws an exception.
     */
    @Test
    public void testDoubleNegativeInfinity() throws JsonException {
        // Expect the exception
        exception.expect(JsonException.class);
        exception.expectMessage("Invalid Double value: " + Double.NEGATIVE_INFINITY);

        JsonValue.wrap(Double.NEGATIVE_INFINITY);
    }

    /**
     * Test trying to wrap Double.POSITIVE_INFINITY throws an exception.
     */
    @Test
    public void testDoublePositiveInfinity() throws JsonException {
        // Expect the exception
        exception.expect(JsonException.class);
        exception.expectMessage("Invalid Double value: " + Double.POSITIVE_INFINITY);

        JsonValue.wrap(Double.POSITIVE_INFINITY);
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
}
