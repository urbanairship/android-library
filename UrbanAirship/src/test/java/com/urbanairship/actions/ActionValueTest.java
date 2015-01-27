package com.urbanairship.actions;

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
public class ActionValueTest {

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
     * Test wrapping a JSONObject.
     */
    @Test
    public void testWrapJSONObject() throws JSONException, ActionValue.ActionValueException {
        JSONObject jsonObject = new JSONObject(primitiveMap);
        jsonObject.put("map", new JSONObject(primitiveMap));
        jsonObject.put("collection", new JSONArray(primitiveList));

        Map<String, ActionValue> actionValueMap = ActionValue.wrap(jsonObject).getMap();

        // Validate all the values in the map
        for (Map.Entry<String, ActionValue> entry : actionValueMap.entrySet()) {
            // Wrap the value individual so all the values will be coerced the same
            ActionValue value = ActionValue.wrap(jsonObject.get(entry.getKey()));
            assertEquals(value, entry.getValue());
        }

        assertEquals(jsonObject.length(), actionValueMap.size());
    }

    /**
     * Test wrapping a JSONArray.
     */
    @Test
    public void testWrapJSONArray() throws JSONException, ActionValue.ActionValueException {
        JSONArray jsonArray = new JSONArray(primitiveList);
        jsonArray.put(new JSONObject(primitiveMap));
        jsonArray.put(new JSONArray(primitiveList));


        List<ActionValue> actionValueList = ActionValue.wrap(jsonArray).getList();

        // Validate all the values in the list
        for (int i = 0; i < actionValueList.size(); i++) {
            // Wrap the value individual so all the values will be coerced the same
            ActionValue value = ActionValue.wrap(jsonArray.get(i));
            assertEquals(value, actionValueList.get(i));
        }

        assertEquals(jsonArray.length(), actionValueList.size());
    }

    /**
     * Test wrapping a map.
     */
    @Test
    public void testWrapMap() throws ActionValue.ActionValueException {
        Map<String, Object> map = new HashMap<>(primitiveMap);
        map.put("map", primitiveMap);
        map.put("collection",primitiveList);

        Map<String, ActionValue> actionValueMap = ActionValue.wrap(map).getMap();
        assertNotNull(actionValueMap);

        // Validate all the values in the map
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            // Wrap the value individual so all the values will be coerced the same
            ActionValue value = ActionValue.wrap(entry.getValue());
            assertEquals(value, actionValueMap.get(entry.getKey()));
        }

        assertEquals(map.size(), actionValueMap.size());
    }


    /**
     * Test wrapping a list.
     */
    @Test
    public void testWrapList() throws ActionValue.ActionValueException {
        List<Object> list = new ArrayList<>(primitiveList);

        // Add a map and the list as child items
        list.add(primitiveMap);
        list.add(primitiveList);

        List<ActionValue> actionValueList = ActionValue.wrap(list).getList();

        // Validate all the values are in the list properly
        for (int i = 0; i < actionValueList.size(); i++) {
            // Wrap the value individual so all the values will be coerced the same
            ActionValue wrappedValue = ActionValue.wrap(list.get(i));
            assertEquals(wrappedValue, actionValueList.get(i));
        }

        assertEquals(list.size(), actionValueList.size());
    }

    /**
     * Test wrapping an array.
     */
    @Test
    public void testWrapArray() throws ActionValue.ActionValueException {
        List<Object> list = new ArrayList<>(primitiveList);

        // Add a map and the list as child items
        list.add(primitiveMap);
        list.add(primitiveList);

        Object[] array = list.toArray(new Object[list.size()]);

        List<ActionValue> actionValueList = ActionValue.wrap(array).getList();
        assertNotNull(actionValueList);

        // Validate all the values are in the list properly
        for (int i = 0; i < array.length; i++) {
            // Wrap the value individual so all the values will be coerced the same
            ActionValue wrappedValue = ActionValue.wrap(array[i]);
            assertEquals(wrappedValue, actionValueList.get(i));
        }

        assertEquals(array.length, actionValueList.size());
    }

    /**
     * Test wrapping integers.
     */
    @Test
    public void testWrapInteger() throws ActionValue.ActionValueException {
        // bytes and shorts are converted to Integer
        assertEquals(1, ActionValue.wrap((byte)1).getInt(0));
        assertEquals(1, ActionValue.wrap((short)1).getInt(0));

        assertEquals(1, ActionValue.wrap(1).getInt(0));
    }

    /**
     * Test wrapping longs.
     */
    @Test
    public void testWrapLong() throws ActionValue.ActionValueException {
        assertEquals(1l, ActionValue.wrap(1l).getLong(0));
    }

    /**
     * Test wrapping doubles.
     */
    @Test
    public void testWrapDouble() throws ActionValue.ActionValueException {
        // floats are converted to doubles
        assertEquals(1.0d, ActionValue.wrap(1.0f).getDouble(0));

        assertEquals(1.0d, ActionValue.wrap(1.0d).getDouble(0));
    }

    /**
     * Test wrapping booleans.
     */
    @Test
    public void testWrapBoolean() throws ActionValue.ActionValueException {
        assertTrue(ActionValue.wrap(true).getBoolean(false));
        assertFalse(ActionValue.wrap(false).getBoolean(true));
    }

    /**
     * Test wrapping strings.
     */
    @Test
    public void testWrapString() throws ActionValue.ActionValueException, MalformedURLException, URISyntaxException {
        assertEquals("Hello", ActionValue.wrap("Hello").getString());
        assertEquals("c", ActionValue.wrap('c').getString());
    }

    /**
     * Test wrapping null.
     */
    @Test
    public void testWrapNull() throws ActionValue.ActionValueException {
        assertTrue(ActionValue.wrap(null).isNull());
    }

    /**
     * Test ActionValue toString produces valid JSON output.
     */
    @Test
    public void testToString() throws ActionValue.ActionValueException, JSONException {
        // Primitives
        assertEquals("\"Hello\"", ActionValue.wrap("Hello").toString());
        assertEquals("1", ActionValue.wrap(1).toString());
        assertEquals(String.valueOf(Long.MAX_VALUE), ActionValue.wrap(Long.MAX_VALUE).toString());
        assertEquals("1.2", ActionValue.wrap(1.2).toString());
        assertEquals("true", ActionValue.wrap(true).toString());
        assertEquals("false", ActionValue.wrap(false).toString());
        assertEquals("null", ActionValue.NULL.toString());

        // Map
        Map<String, Object> map = new HashMap<>(primitiveMap);
        map.put("map", primitiveMap);
        map.put("collection", primitiveList);

        String expected = "{\"short\":3,\"byte\":2,\"char\":\"c\",\"String\":\"String\",\"int\":1," +
                "\"map\":{\"short\":3,\"char\":\"c\",\"byte\":2,\"int\":1,\"String\":\"String\"," +
                "\"boolean\":true,\"double\":1.2},\"boolean\":true,\"collection\":" +
                "[\"String\",1.2,false,1,\"c\",2,3],\"double\":1.2}";

        assertEquals(expected, ActionValue.wrap(map).toString());

        // List
        List<Object> list = new ArrayList<>(primitiveList);
        list.add(primitiveMap);
        list.add(primitiveList);

        expected = "[\"String\",1.2,false,1,\"c\",2,3,{\"short\":3,\"char\":\"c\",\"byte\":2," +
                "\"int\":1,\"String\":\"String\",\"boolean\":true,\"double\":1.2},[\"String\",1.2," +
                "false,1,\"c\",2,3]]";

        assertEquals(expected, ActionValue.wrap(list).toString());
    }


    /**
     * Test parsing a valid JSON String produces the equivalent ActionValue.
     */
    @Test
    public void testParseString() throws ActionValue.ActionValueException, JSONException {
        assertEquals(ActionValue.wrap("Hello"), ActionValue.parseString("\"Hello\""));
        assertEquals(ActionValue.wrap(1), ActionValue.parseString("1"));
        assertEquals(ActionValue.wrap(true), ActionValue.parseString("true"));
        assertEquals(ActionValue.wrap(false), ActionValue.parseString("false"));
        assertEquals(ActionValue.wrap(Long.MAX_VALUE), ActionValue.parseString(String.valueOf(Long.MAX_VALUE)));
        assertEquals(ActionValue.wrap(1.4), ActionValue.parseString(String.valueOf(1.4)));
        assertEquals(ActionValue.NULL, ActionValue.parseString("null"));
        assertEquals(ActionValue.NULL, ActionValue.parseString(null));

        // Map
        JSONObject json = new JSONObject(primitiveMap);
        json.put("map", new JSONObject(primitiveMap));
        json.put("collection", new JSONArray(primitiveList));
        assertEquals(ActionValue.wrap(json), ActionValue.parseString(json.toString()));

        // List
        JSONArray jsonArray = new JSONArray(primitiveList);
        assertEquals(ActionValue.wrap(jsonArray), ActionValue.parseString(jsonArray.toString()));
    }


    /**
     * Test trying to wrap Double.NaN throws an exception.
     */
    @Test
    public void testDoubleNAN() throws ActionValue.ActionValueException {
        // Expect the exception
        exception.expect(ActionValue.ActionValueException.class);
        exception.expectMessage("Invalid Double value: " + Double.NaN);

        ActionValue.wrap(Double.NaN);
    }

    /**
     * Test trying to wrap Double.NEGATIVE_INFINITY throws an exception.
     */
    @Test
    public void testDoubleNegativeInfinity() throws ActionValue.ActionValueException {
        // Expect the exception
        exception.expect(ActionValue.ActionValueException.class);
        exception.expectMessage("Invalid Double value: " + Double.NEGATIVE_INFINITY);

        ActionValue.wrap(Double.NEGATIVE_INFINITY);
    }

    /**
     * Test trying to wrap Double.POSITIVE_INFINITY throws an exception.
     */
    @Test
    public void testDoublePositiveInfinity() throws ActionValue.ActionValueException {
        // Expect the exception
        exception.expect(ActionValue.ActionValueException.class);
        exception.expectMessage("Invalid Double value: " + Double.POSITIVE_INFINITY);

        ActionValue.wrap(Double.POSITIVE_INFINITY);
    }
}
