/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class JSONRichPushTestUtilsTest extends BaseTestCase {

    /**
     * Test converting a JSONObject with simple values
     */
    @Test
    public void testConvertToMap() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("string", "some-string");
        jsonObject.put("integer", 3);
        jsonObject.put("boolean", false);
        jsonObject.put("double", 10.0);
        jsonObject.put("object", new Object());
        jsonObject.put("null", null);

        Map<String, Object> map = JSONUtils.convertToMap(jsonObject);
        assertEquals("Map size mismatch", map.size(), jsonObject.length());

        for (String key : map.keySet()) {
            assertEquals("Value mismatch", map.get(key), jsonObject.get(key));
        }
    }

    /**
     * Test converting an empty JSONObject
     */
    @Test
    public void testConvertEmptyObject() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        Map<String, Object> map = JSONUtils.convertToMap(jsonObject);
        assertTrue("Map should be empty", map.isEmpty());
    }

    /**
     * Test converting a null JSONObject
     */
    @Test
    public void testConvertNullObject() throws JSONException {
        Map<String, Object> map = JSONUtils.convertToMap(null);
        assertTrue("Map should be empty", map.isEmpty());
    }

    /**
     * Test converting JSONObject with child JSONArrays and JSONObjects
     */
    @Test
    public void testConvertToMapChildJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();

        // Add a child JSONObject
        JSONObject childJSONObject = new JSONObject();
        childJSONObject.put("object", "value");
        jsonObject.put("jsonObject", childJSONObject);

        //  Add a child JSONArray
        jsonObject.put("jsonArray", createJSONArray("child"));

        Map<String, Object> map = JSONUtils.convertToMap(jsonObject);
        assertEquals("Map size mismatch", map.size(), jsonObject.length());

        // Check the child array
        List<Object> childObjects = (List<Object>) map.get("jsonArray");
        assertEquals("List size mismatch", childObjects.size(), 1);
        assertEquals("Value mismatch", childObjects.get(0), "child");

        // Check child map
        Map<String, Object> childObjectMap = (Map<String, Object>) map.get("jsonObject");
        assertEquals("Map size mismatch", childObjectMap.size(), 1);
        assertEquals("Value mismatch", childObjectMap.get("object"), "value");
    }

    /**
     * Test converting JSONArray to a List with simple values
     */
    @Test
    public void testConvertToList() throws JSONException {
        JSONArray jsonArray = createJSONArray(true, 10.0, 3,
                new Object(), null, "hello");

        List<Object> objects = JSONUtils.convertToList(jsonArray);

        assertEquals("List size mismatch", objects.size(), jsonArray.length());

        // Check only the base types
        for (int i = 0; i < objects.size(); i++) {
            assertEquals("Value mismatch", objects.get(i), jsonArray.opt(i));
        }
    }

    /**
     * Test converting JSONObject to List with child JSONArrays and JSONObjects
     */
    @Test
    public void testConvertToArrayChildJSON() throws JSONException {
        // Create a child JSONObject
        JSONObject childJSONObject = new JSONObject();
        childJSONObject.put("object", "value");

        // Create a child JSONArray
        JSONArray childJSONArray = createJSONArray("child");

        // New array with the above objects
        JSONArray jsonArray = createJSONArray(childJSONArray, childJSONObject);

        List<Object> objects = JSONUtils.convertToList(jsonArray);

        assertEquals("List size mismatch", objects.size(), jsonArray.length());

        // Check child array
        List<Object> childObjects = (List<Object>) objects.get(0);
        assertEquals("List size mismatch", childObjects.size(), 1);
        assertEquals("Value mismatch", childObjects.get(0), "child");

        // Check child map
        Map<String, Object> childObjectMap = (Map<String, Object>) objects.get(1);
        assertEquals("Map size mismatch", childObjectMap.size(), 1);
        assertEquals("Value mismatch", childObjectMap.get("object"), "value");
    }


    /**
     * Helper method that creates a json array with the passed in values
     *
     * @param values Values to add to the json array
     * @return A JSONArray with the supplied values
     */
    private JSONArray createJSONArray(Object... values) {
        JSONArray jsonArray = new JSONArray();

        for (Object value : values) {
            jsonArray.put(value);
        }

        return jsonArray;
    }
}
