/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PreferenceDataStoreTest {

    private var context: Context = ApplicationProvider.getApplicationContext()
    private var testPrefs = PreferenceDataStore.inMemoryStore(context)

    val mockDao = mockk<PreferenceDataDao>(relaxed = true)
    val mockDb = mockk<PreferenceDataDatabase> {
        every { dao } returns mockDao

    }

    @Test
    public fun testIsSet() {
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
    public fun testString() {
        testPrefs.put("value", "oh hi")
        Assert.assertEquals("oh hi", testPrefs.getString("value", "oh hi"))

        testPrefs.put("value", null as String?)
        Assert.assertNull(testPrefs.getString("value", null))
    }

    /**
     * Test saving longs.
     */
    @Test
    public fun testLong() {
        testPrefs.put("value", 123L)
        Assert.assertEquals(123, testPrefs.getLong("value", -1))
    }

    /**
     * Test saving ints.
     */
    @Test
    public fun testInt() {
        testPrefs.put("value", 123)
        Assert.assertEquals(123, testPrefs.getInt("value", -1).toLong())
    }

    /**
     * Test saving booleans.
     */
    @Test
    public fun testBoolean() {
        testPrefs.put("value", true)
        Assert.assertTrue(testPrefs.getBoolean("value", false))

        testPrefs.put("value", false)
        Assert.assertFalse(testPrefs.getBoolean("value", true))
    }

    /**
     * Test saving json values.
     */
    @Test
    public fun testJsonValue() {
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
    public fun testJsonSerializable() {
        val map = mapOf(
            "string" to "string",
            "int" to 123,
            "double" to 123.123
        )
        val value = JsonValue.wrap(map)

        val testObject = JsonSerializable { value }

        testPrefs.put("value", testObject)
        Assert.assertEquals(value, testPrefs.getJsonValue("value"))

        testPrefs.put("value", null as JsonSerializable?)
        Assert.assertTrue(testPrefs.getJsonValue("value").isNull)
    }

    /**
     * Test saving json serializable when toJson returns null.
     */
    @Test
    public fun testJsonSerializableNullJsonValue() {
        val testObject = JsonSerializable { JsonValue.NULL }

        testPrefs.put("value", testObject)
        Assert.assertTrue(testPrefs.getJsonValue("value").isNull)
    }

    /**
     * When batch load fails, per-key fallback should delete corrupt rows and still load others.
     */
    @Test
    public fun fallbackLoadDeletesKeyWhenQueryValueThrows() {

        every { mockDao.getPreferences() } throws RuntimeException("batch load failed")
        every { mockDao.queryKeys() } returns listOf("bad", "good")
        every { mockDao.queryValue("bad") } throws RuntimeException("row read failed")
        every { mockDao.queryValue("good") } returns PreferenceData("good", "saved")

        val store = PreferenceDataStore(mockDb)
        store.loadPreferences()

        verify { mockDao.delete("bad") }
        Assert.assertEquals("saved", store.getString("good", null))
    }

    /**
     * When batch load fails and a row has a null value, that key should be deleted from the store.
     */
    @Test
    public fun fallbackLoadDeletesKeyWhenValueIsNull() {

        every { mockDao.getPreferences() } throws RuntimeException("batch load failed")
        every { mockDao.queryKeys() } returns listOf("empty", "good")
        every { mockDao.queryValue("empty") } returns PreferenceData("empty", null)
        every { mockDao.queryValue("good") } returns PreferenceData("good", "ok")

        val store = PreferenceDataStore(mockDb)
        store.loadPreferences()

        verify { mockDao.delete("empty") }
        Assert.assertEquals("ok", store.getString("good", null))
    }
}
