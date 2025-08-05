/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceDataStoreTest {

    private var context: Context = ApplicationProvider.getApplicationContext()
    private var testPrefs = PreferenceDataStore.inMemoryStore(context)

    @Test
    fun testIsSet() {
        Assert.assertFalse(testPrefs.isSet("neat"))
        testPrefs.put("neat", "oh hi")
        Assert.assertTrue(testPrefs.isSet("neat"))

        testPrefs.remove("neat")
        Assert.assertFalse(testPrefs.isSet("neat"))
    }

    /**
     * Test saving string values.
     */
    @Test
    fun testString() {
        testPrefs.put("value", "oh hi")
        Assert.assertEquals("oh hi", testPrefs.getString("value", "oh hi"))

        testPrefs.put("value", null as String?)
        Assert.assertNull(testPrefs.getString("value", null))
    }

    /**
     * Test saving longs.
     */
    @Test
    fun testLong() {
        testPrefs.put("value", 123L)
        Assert.assertEquals(123, testPrefs.getLong("value", -1))
    }

    /**
     * Test saving ints.
     */
    @Test
    fun testInt() {
        testPrefs.put("value", 123)
        Assert.assertEquals(123, testPrefs.getInt("value", -1).toLong())
    }

    /**
     * Test saving booleans.
     */
    @Test
    fun testBoolean() {
        testPrefs.put("value", true)
        Assert.assertTrue(testPrefs.getBoolean("value", false))

        testPrefs.put("value", false)
        Assert.assertFalse(testPrefs.getBoolean("value", true))
    }

    /**
     * Test saving json values.
     */
    @Test
    fun testJsonValue() {
        val map = mapOf(
            "string" to "string",
            "int" to 123,
            "double" to 123.123
        )
        val value = JsonValue.wrap(map)

        testPrefs.put("value", value)
        Assert.assertEquals(value, testPrefs.getJsonValue("value"))

        testPrefs.put("value", null as JsonValue?)
        Assert.assertTrue(testPrefs.getJsonValue("value").isNull)
    }

    /**
     * Test saving json serializable values.
     */
    @Test
    fun testJsonSerializable() {
        val map = mapOf(
            "string" to "string",
            "int" to 123,
            "double" to 123.123
        )
        val value = JsonValue.wrap(map)

        val testObject: JsonSerializable = object : JsonSerializable {
            override fun toJsonValue(): JsonValue {
                return value
            }
        }

        testPrefs.put("value", testObject)
        Assert.assertEquals(value, testPrefs.getJsonValue("value"))

        testPrefs.put("value", null as JsonSerializable?)
        Assert.assertTrue(testPrefs.getJsonValue("value").isNull)
    }

    /**
     * Test saving json serializable when toJson returns null.
     */
    @Test
    fun testJsonSerializableNullJsonValue() {
        val testObject: JsonSerializable = object : JsonSerializable {
            override fun toJsonValue(): JsonValue {
                return JsonValue.NULL
            }
        }

        testPrefs.put("value", testObject)
        Assert.assertTrue(testPrefs.getJsonValue("value").isNull)
    }
}
