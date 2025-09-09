/* Copyright Airship and Contributors */
package com.urbanairship.json

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JsonValueTest {

    private val primitiveMap = mapOf(
        "double" to 1.2,
        "boolean" to true,
        "int" to 1,
        "char" to 'c',
        "byte" to 2.toByte(),
        "short" to 3.toShort(),
        "String" to "String"
    )
    private val primitiveList = listOf("String", 1.2, false, 1, 'c', 2.toByte(), 3.toShort())

    /**
     * Test wrapping a JsonSerializable object returns the objects JsonValue.
     */
    @Test
    public fun testWrapJsonSerializable() {
        val serializableValue = JsonValue.wrap("some value")
        val jsonSerializable = JsonSerializable { serializableValue }

        val jsonValue = JsonValue.wrap(jsonSerializable)
        Assert.assertEquals(serializableValue, jsonValue)
    }

    /**
     * Test wrapping a JSONObject.
     */
    @Test
    public fun testWrapJSONObject() {
        val jsonObject = JSONObject(primitiveMap)
        jsonObject.put("map", JSONObject(primitiveMap))
        jsonObject.put("collection", JSONArray(primitiveList))

        val jsonValue = JsonValue.wrap(jsonObject)
        Assert.assertTrue(jsonValue.value is JsonMap)

        val jsonMap = jsonValue.map

        // Validate all the values in the map
        jsonValue.map?.forEach { (key, value) ->
            // Wrap the value individual so all the values will be coerced the same
            val value1 = JsonValue.wrap(jsonObject[key])
            Assert.assertEquals(value1, value)
        }

        Assert.assertEquals(jsonObject.length(), jsonMap?.size())
    }

    /**
     * Test wrapping a JSONArray.
     */
    @Test
    public fun testWrapJSONArray() {
        val jsonArray = JSONArray(primitiveList)
        jsonArray.put(JSONObject(primitiveMap))
        jsonArray.put(JSONArray(primitiveList))

        val jsonValue = JsonValue.wrap(jsonArray)
        Assert.assertTrue(jsonValue.value is JsonList)

        val jsonList = jsonValue.list
        jsonValue.list?.forEachIndexed { index, ogValue ->
            val value = JsonValue.wrap(jsonArray[index])
            Assert.assertEquals(value, ogValue)
        }

        Assert.assertEquals(jsonArray.length(), jsonList?.size())
    }

    /**
     * Test wrapping a map.
     */
    @Test
    public fun testWrapMap() {
        val map = primitiveMap.toMutableMap()
            .apply {
                put("map", primitiveMap)
                put("collection", primitiveList)
            }

        val jsonValue = JsonValue.wrap(map)
        Assert.assertTrue(jsonValue.value is JsonMap)

        val jsonMap = jsonValue.map
        Assert.assertNotNull(jsonMap)

        map.forEach { (key, value1) ->
            val value = JsonValue.wrap(value1)
            Assert.assertEquals(value, jsonMap?.get(key))
        }
        Assert.assertEquals(map.size, jsonMap?.size())
    }

    /**
     * Test wrapping a list.
     */
    @Test
    public fun testWrapList() {
        val list = primitiveList.toMutableList()
            .apply {
                add(primitiveMap)
                add(primitiveList)
            }

        val jsonValue = JsonValue.wrap(list)
        Assert.assertTrue(jsonValue.value is JsonList)

        val jsonList = jsonValue.list
        jsonValue.list?.forEachIndexed { index, ogValue ->
            val value = JsonValue.wrap(list[index])
            Assert.assertEquals(value, ogValue)
        }

        Assert.assertEquals(list.size, jsonList?.size())
    }

    /**
     * Test wrapping an array.
     */
    @Test
    public fun testWrapArray() {
        val list = primitiveList.toMutableList()
            .apply {
                add(primitiveMap)
                add(primitiveList)
            }

        val array = list.toTypedArray()

        val jsonValue = JsonValue.wrap(array)
        Assert.assertTrue(jsonValue.value is JsonList)

        val jsonList = jsonValue.list
        Assert.assertNotNull(jsonList)

        // Validate all the values are in the list properly
        for (i in array.indices) {
            // Wrap the value individual so all the values will be coerced the same
            val wrappedValue = JsonValue.wrap(array[i])
            Assert.assertEquals(wrappedValue, jsonList?.get(i))
        }

        Assert.assertEquals(array.size, jsonList?.size())
    }

    /**
     * Test wrapping integers.
     */
    @Test
    public fun testWrapInteger() {
        // bytes and shorts are converted to Integer
        Assert.assertEquals(1, JsonValue.wrap(1.toByte().toInt()).getInt(0))
        Assert.assertEquals(1, JsonValue.wrap(1.toShort().toInt()).getInt(0))
        Assert.assertEquals(1, JsonValue.wrap(1).getInt(0))

        Assert.assertTrue(JsonValue.wrap(1).value is Int)
    }

    /**
     * Test wrapping longs.
     */
    @Test
    public fun testWrapLong() {
        Assert.assertEquals(1L, JsonValue.wrap(1L).getLong(0))
        Assert.assertTrue(JsonValue.wrap(1L).value is Long)
    }

    /**
     * Test wrapping doubles.
     */
    @Test
    public fun testWrapDouble() {
        // floats are converted to doubles
        Assert.assertEquals(1.0, JsonValue.wrap(1.0).getDouble(0.0), 0.0001)
        Assert.assertTrue(JsonValue.wrap(1.0).value is Double)

        Assert.assertEquals(1.0, JsonValue.wrap(1.0).getDouble(0.0), 0.0001)
        Assert.assertTrue(JsonValue.wrap(1.0).value is Double)
    }

    /**
     * Test wrapping booleans.
     */
    @Test
    public fun testWrapBoolean() {
        Assert.assertTrue(JsonValue.wrap(true).getBoolean(false))
        Assert.assertTrue(JsonValue.wrap(true).value is Boolean)

        Assert.assertFalse(JsonValue.wrap(false).getBoolean(true))
        Assert.assertTrue(JsonValue.wrap(false).value is Boolean)
    }

    /**
     * Test wrapping strings.
     */
    @Test
    public fun testWrapString() {
        Assert.assertEquals("Hello", JsonValue.wrap("Hello").string)
        Assert.assertTrue(JsonValue.wrap("Hello").value is String)

        Assert.assertEquals("c", JsonValue.wrap('c').string)
        Assert.assertTrue(JsonValue.wrap('c').value is String)
    }

    /**
     * Test wrapping null.
     */
    @Test
    public fun testWrapNull() {
        Assert.assertTrue(JsonValue.wrap(`object` = null).isNull)
    }

    /**
     * Test JsonValue toString produces valid JSON output.
     */
    @Test
    public fun testToString() {
        // Primitives
        Assert.assertEquals("\"Hello\"", JsonValue.wrap("Hello").toString())
        Assert.assertEquals("1", JsonValue.wrap(1).toString())
        Assert.assertEquals(Long.MAX_VALUE.toString(), JsonValue.wrap(Long.MAX_VALUE).toString())
        Assert.assertEquals("1.2", JsonValue.wrap(1.2).toString())
        Assert.assertEquals("true", JsonValue.wrap(true).toString())
        Assert.assertEquals("false", JsonValue.wrap(false).toString())
        Assert.assertEquals("null", JsonValue.NULL.toString())

        // Map
        val map = primitiveMap.toMutableMap()
            .apply {
                put("map", primitiveMap)
                put("collection", primitiveList)
            }

        var expected = JsonValue.parseString("""
            {
              "String": "String",
              "boolean": true,
              "byte": 2,
              "char": "c",
              "collection": [
                "String",
                1.2,
                false,
                1,
                "c",
                2,
                3
              ],
              "double": 1.2,
              "int": 1,
              "map": {
                "String": "String",
                "boolean": true,
                "byte": 2,
                "char": "c",
                "double": 1.2,
                "int": 1,
                "short": 3
              },
              "short": 3
            }
        """.trimIndent()
        )

        Assert.assertEquals(expected, JsonValue.wrap(map))

        // List
        val list = primitiveList.toMutableList().apply { add(primitiveList) }

        expected =
            JsonValue.parseString("""
                [
                  "String",
                  1.2,
                  false,
                  1,
                  "c",
                  2,
                  3,
                  [
                    "String",
                    1.2,
                    false,
                    1,
                    "c",
                    2,
                    3
                  ]
                ]
            """.trimIndent())

        Assert.assertEquals(expected.list, JsonValue.wrap(list).list)
    }

    /**
     * Test parsing a valid JSON String produces the equivalent JsonValue.
     */
    @Test
    public fun testParseString() {
        Assert.assertEquals(JsonValue.wrap("Hello"), JsonValue.parseString("\"Hello\""))
        Assert.assertEquals(JsonValue.wrap(1), JsonValue.parseString("1"))
        Assert.assertEquals(JsonValue.wrap(true), JsonValue.parseString("true"))
        Assert.assertEquals(JsonValue.wrap(false), JsonValue.parseString("false"))
        Assert.assertEquals(JsonValue.wrap(Long.MAX_VALUE), JsonValue.parseString(Long.MAX_VALUE.toString()))
        Assert.assertEquals(JsonValue.wrap(1.4), JsonValue.parseString(1.4.toString()))
        Assert.assertEquals(JsonValue.NULL, JsonValue.parseString("null"))
        Assert.assertEquals(JsonValue.NULL, JsonValue.parseString(null))

        // Test empty map
        Assert.assertEquals(JsonMap(null), JsonValue.parseString("{}").map)

        // Test empty list
        Assert.assertEquals(JsonList(null), JsonValue.parseString("[]").list)

        // Map
        val json = JSONObject(primitiveMap)
        json.put("map", JSONObject(primitiveMap))
        json.put("collection", JSONArray(primitiveList))
        Assert.assertEquals(JsonValue.wrap(json), JsonValue.parseString(json.toString()))

        // List
        val jsonArray = JSONArray(primitiveList)
        Assert.assertEquals(JsonValue.wrap(jsonArray), JsonValue.parseString(jsonArray.toString()))
    }

    /**
     * Test trying to wrap Double.NaN throws an exception.
     */
    @Test
    public fun testDoubleNAN() {
        Assert.assertEquals(JsonValue.NULL, JsonValue.wrap(Double.NaN))
    }

    /**
     * Test trying to wrap Double.NEGATIVE_INFINITY throws an exception.
     */
    @Test
    public fun testDoubleNegativeInfinity() {
        Assert.assertEquals(JsonValue.NULL, JsonValue.wrap(Double.NEGATIVE_INFINITY))
    }

    /**
     * Test trying to wrap Double.POSITIVE_INFINITY throws an exception.
     */
    @Test
    public fun testDoublePositiveInfinity() {
        Assert.assertEquals(JsonValue.NULL, JsonValue.wrap(Double.POSITIVE_INFINITY))
    }

    /**
     * Test saving and reading a JsonValue from a parcel.
     */
    @Test
    public fun testParcelable() {
        val jsonValue = JsonValue.wrap(primitiveMap)

        // Write the push message to a parcel
        val parcel = Parcel.obtain()
        jsonValue.writeToParcel(parcel, 0)

        // Reset the parcel so we can read it
        parcel.setDataPosition(0)

        // Create the message from the parcel
        val fromParcel = JsonValue.CREATOR.createFromParcel(parcel)

        // Validate the data
        Assert.assertEquals(jsonValue, fromParcel)
    }

    /**
     * Test isNull is true for null values.
     */
    @Test
    public fun testIsNull() {
        Assert.assertTrue(JsonValue.NULL.isNull)
        Assert.assertTrue(JsonValue.wrap(null as Any?).isNull)
    }

    /**
     * Test isString is true for String values.
     */
    @Test
    public fun testIsString() {
        Assert.assertTrue(JsonValue.wrap('c').isString)
        Assert.assertTrue(JsonValue.wrap("hi").isString)
    }

    /**
     * Test isInteger is true only for int values.
     */
    @Test
    public fun testIsInteger() {
        Assert.assertTrue(JsonValue.wrap(1).isInteger)

        Assert.assertFalse(JsonValue.wrap(1L).isInteger)
        Assert.assertFalse(JsonValue.wrap(1.0).isInteger)
        Assert.assertFalse(JsonValue.wrap(1.0).isInteger)
    }

    /**
     * Test isLong is true only for longs.
     */
    @Test
    public fun testIsLong() {
        Assert.assertTrue(JsonValue.wrap(1L).isLong)

        Assert.assertFalse(JsonValue.wrap(1).isLong)
        Assert.assertFalse(JsonValue.wrap(1.0).isLong)
        Assert.assertFalse(JsonValue.wrap(1.0).isLong)
    }

    /**
     * Test isDouble is true for floats and doubles.
     */
    @Test
    public fun testIsDouble() {
        Assert.assertTrue(JsonValue.wrap(1.0).isDouble)
        Assert.assertTrue(JsonValue.wrap(1.0).isDouble)

        Assert.assertFalse(JsonValue.wrap(1L).isDouble)
        Assert.assertFalse(JsonValue.wrap(1).isDouble)
    }

    /**
     * Test isNumber is true for any number types.
     */
    @Test
    public fun testIsNumber() {
        Assert.assertTrue(JsonValue.wrap(1.0).isNumber)
        Assert.assertTrue(JsonValue.wrap(1.0).isNumber)
        Assert.assertTrue(JsonValue.wrap(1).isNumber)
        Assert.assertTrue(JsonValue.wrap(1L).isNumber)
    }

    /**
     * Test isBoolean is true for any boolean values.
     */
    @Test
    public fun testIsBoolean() {
        Assert.assertTrue(JsonValue.wrap(true).isBoolean)
        Assert.assertTrue(JsonValue.wrap(false).isBoolean)
    }

    /**
     * Test isJsonMap is true for map values.
     */
    @Test
    public fun testIsJsonMap() {
        Assert.assertTrue(JsonValue.wrap(mapOf<String, String>()).isJsonMap)
    }

    /**
     * Test isJsonList is true for list values.
     */
    @Test
    public fun testIsJsonList() {
        Assert.assertTrue(JsonValue.wrap(emptyList<String>()).isJsonList)
    }

    /**
     * Tests equals handling for numbers.
     */
    @Test
    public fun testNumberEquals() {
        // Double
        Assert.assertFalse(JsonValue.wrap(1) == JsonValue.wrap(1.5))
        Assert.assertFalse(JsonValue.wrap(1L) == JsonValue.wrap(1.5))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(1.5))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(1.5))
        Assert.assertTrue(JsonValue.wrap(1.5) == JsonValue.wrap(1.5))
        Assert.assertTrue(JsonValue.wrap(1.5) == JsonValue.wrap(1.5))

        // Float
        Assert.assertFalse(JsonValue.wrap(1) == JsonValue.wrap(1.5))
        Assert.assertFalse(JsonValue.wrap(1L) == JsonValue.wrap(1.5))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(1.5))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(1.5))
        Assert.assertTrue(JsonValue.wrap(1.5) == JsonValue.wrap(1.5))
        Assert.assertTrue(JsonValue.wrap(1.5) == JsonValue.wrap(1.5))

        // Int
        Assert.assertFalse(JsonValue.wrap(1) == JsonValue.wrap(2))
        Assert.assertFalse(JsonValue.wrap(1L) == JsonValue.wrap(2))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(2))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(2))
        Assert.assertTrue(JsonValue.wrap(2L) == JsonValue.wrap(2))
        Assert.assertTrue(JsonValue.wrap(2) == JsonValue.wrap(2))
        Assert.assertTrue(JsonValue.wrap(2.0) == JsonValue.wrap(2))
        Assert.assertTrue(JsonValue.wrap(2.0) == JsonValue.wrap(2))

        // Long
        Assert.assertFalse(JsonValue.wrap(1) == JsonValue.wrap(2L))
        Assert.assertFalse(JsonValue.wrap(1L) == JsonValue.wrap(2L))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(2L))
        Assert.assertFalse(JsonValue.wrap(1.0) == JsonValue.wrap(2L))
        Assert.assertTrue(JsonValue.wrap(2L) == JsonValue.wrap(2L))
        Assert.assertTrue(JsonValue.wrap(2) == JsonValue.wrap(2L))
        Assert.assertTrue(JsonValue.wrap(2.0) == JsonValue.wrap(2L))
        Assert.assertTrue(JsonValue.wrap(2.0) == JsonValue.wrap(2L))
    }
}
