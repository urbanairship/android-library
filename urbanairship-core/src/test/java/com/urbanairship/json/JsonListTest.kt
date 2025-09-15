/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue.Companion.wrap
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JsonListTest {

    private var jsonList: JsonList? = null

    @Before
    @Throws(JsonException::class)
    public fun setUp() {
        jsonList = wrap(arrayOf("first-value", "second-value", null)).list
        Assert.assertNotNull(jsonList)
    }

    /**
     * Test creating a new JsonList with a null list.
     */
    @Test
    public fun testCreateNull() {
        val emptyList = JsonList(null)
        Assert.assertEquals(0, emptyList.size())
        Assert.assertTrue(emptyList.isEmpty)
    }

    /**
     * Test toString produces a JSON encoded String.
     */
    @Test
    public fun testToString() {
        val expected = "[\"first-value\",\"second-value\"]"
        Assert.assertEquals(expected, jsonList.toString())
    }

    /**
     * Test toString on an empty list produces a JSON encoded String.
     */
    @Test
    public fun testEmptyMapToString() {
        Assert.assertEquals("[]", JsonList(null).toString())
    }
}
