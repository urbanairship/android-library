/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonMap.Companion.newBuilder
import com.urbanairship.json.JsonValue.Companion.parseString
import com.urbanairship.json.JsonValue.Companion.wrap
import com.urbanairship.json.JsonValue.Companion.wrapOpt
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsonMapTest {

    private lateinit var jsonMap: JsonMap

    @Before
    @Throws(JsonException::class)
    fun setUp() {
        val map = mapOf(
            "null-key" to null,
            "some-key" to "some-value",
            "another-key" to "another-value"
        )

        jsonMap = wrap(map).map!!
        Assert.assertNotNull(jsonMap)
    }

    /**
     * Test creating a new JsonMap with a null map.
     */
    @Test
    fun testCreateNull() {
        val emptyMap = JsonMap(null)
        Assert.assertEquals(0, emptyMap.size())
        Assert.assertTrue(emptyMap.isEmpty)
        Assert.assertNull(emptyMap["Not in map"])
    }

    /**
     * Test getting an optional value returns a null JsonValue instead of null.
     */
    @Test
    fun testOpt() {
        // Verify it gets values that are available
        Assert.assertEquals("some-value", jsonMap.opt("some-key").string)

        // Verify it returns JsonValue.NULL instead of null for unavailable values
        Assert.assertTrue(jsonMap.opt("Not in map").isNull == true)
    }

    /**
     * Test toString produces a JSON encoded String.
     */
    @Test
    fun testToString() {
        val parsedValue = parseString(jsonMap.toString())
        Assert.assertEquals(parsedValue.map, jsonMap)
    }

    /**
     * Test toString on an empty map produces a JSON encoded String.
     */
    @Test
    fun testEmptyMapToString() {
        Assert.assertEquals("{}", JsonMap(null).toString())
    }

    @Test
    fun testMapBuilder() {
        val list = listOf("String", 1.2, false, 1, 'c')

        jsonMap = newBuilder()
            .putAll(jsonMap)
            .put("boolean", true)
            .put("int", 1)
            .put("char", 'c')
            .put("String", "String")
            .put("Empty String", "")
            .put("list", wrapOpt(list))
            .build()

        Assert.assertEquals("", jsonMap["Empty String"]?.string)
        Assert.assertEquals("some-value", jsonMap["some-key"]?.string)
        Assert.assertEquals("another-value", jsonMap["another-key"]?.string)
        Assert.assertEquals(true, jsonMap["boolean"]?.getBoolean(false))
        Assert.assertEquals(1, jsonMap["int"]?.getInt(2))
        Assert.assertEquals("c", jsonMap["char"]?.string)
        Assert.assertEquals("String", jsonMap["String"]?.string)

        Assert.assertEquals("String", jsonMap["list"]?.list?.list?.first()?.string)
        Assert.assertEquals(1.2, jsonMap["list"]?.list?.list?.get(1)?.getDouble(2.2))
        Assert.assertEquals(false, jsonMap["list"]?.list?.list?.get(2)?.getBoolean(true))
        Assert.assertEquals(1, jsonMap["list"]?.list?.list?.get(3)?.getInt(2))
        Assert.assertEquals("c", jsonMap["list"]?.list?.list?.get(4)?.string)
    }
}
