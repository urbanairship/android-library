/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;


public class JsonListTest extends BaseTestCase {

    private JsonList jsonList;

    @Before
    public void setUp() throws JsonException {
        jsonList = JsonValue.wrap(new Object[] { "first-value", "second-value", null }).getList();
        assertNotNull(jsonList);
    }

    /**
     * Test creating a new JsonList with a null list.
     */
    @Test
    public void testCreateNull() throws JsonException, JSONException {
        JsonList emptyList = new JsonList(null);
        assertEquals(0, emptyList.size());
        assertTrue(emptyList.isEmpty());
    }

    /**
     * Test toString produces a JSON encoded String.
     */
    @Test
    public void testToString() {
        String expected = "[\"first-value\",\"second-value\"]";
        assertEquals(expected, jsonList.toString());
    }

    /**
     * Test toString on an empty list produces a JSON encoded String.
     */
    @Test
    public void testEmptyMapToString() {
        assertEquals("[]", new JsonList(null).toString());
    }
}
