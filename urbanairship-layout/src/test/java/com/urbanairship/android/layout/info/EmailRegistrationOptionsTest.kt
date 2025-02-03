package com.urbanairship.android.layout.info

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class EmailRegistrationOptionsTest {
    @Test
    @Throws(JsonException::class)
    public fun testCommercialFromJson() {
        val json = """
        {
           "type": "commercial",
           "commercial_opted_in": true,
           "properties": {
              "cool": "prop"
           }
        }  
        """

        val fromJson = ThomasEmailRegistrationOptions.fromJson(JsonValue.parseString(json))
        val expected = ThomasEmailRegistrationOptions.Commercial(
            optedIn = true,
            properties = jsonMapOf(
                "cool" to "prop"
            ),
        )
        assertEquals(fromJson,  expected)
    }

    @Test
    @Throws(JsonException::class)
    public fun testCommercialNoPropertiesFromJson() {
        val json = """
        {
           "type": "commercial",
           "commercial_opted_in": false
        }  
        """

        val fromJson = ThomasEmailRegistrationOptions.fromJson(JsonValue.parseString(json))
        val expected = ThomasEmailRegistrationOptions.Commercial(
            optedIn = false,
            properties = null
        )
        assertEquals(fromJson,  expected)
    }

    @Test
    @Throws(JsonException::class)
    public fun testTransactionalFromJson() {
        val json = """
        {
           "type": "transactional",
           "properties": {
              "cool": "prop"
           }
        }  
        """

        val fromJson = ThomasEmailRegistrationOptions.fromJson(JsonValue.parseString(json))
        val expected = ThomasEmailRegistrationOptions.Transactional(
            properties = jsonMapOf(
                "cool" to "prop"
            ),
        )
        assertEquals(fromJson,  expected)
    }

    @Test
    @Throws(JsonException::class)
    public fun testTransactionalNoPropertiesFromJson() {
        val json = """
        {
           "type": "transactional"
        }  
        """

        val fromJson = ThomasEmailRegistrationOptions.fromJson(JsonValue.parseString(json))
        val expected = ThomasEmailRegistrationOptions.Transactional(
            properties = null
        )
        assertEquals(fromJson,  expected)
    }

    @Test
    @Throws(JsonException::class)
    public fun testDoubleOptInFromJson() {
        val json = """
        {
           "type": "double_opt_in",
           "properties": {
              "cool": "prop"
           }
        }  
        """

        val fromJson = ThomasEmailRegistrationOptions.fromJson(JsonValue.parseString(json))
        val expected = ThomasEmailRegistrationOptions.DoubleOptIn(
            properties = jsonMapOf(
                "cool" to "prop"
            )
        )
        assertEquals(fromJson,  expected)
    }

    @Test
    @Throws(JsonException::class)
    public fun testDoubleOptInNoPropertiesFromJson() {
        val json = """
        {
           "type": "double_opt_in"
        }  
        """

        val fromJson = ThomasEmailRegistrationOptions.fromJson(JsonValue.parseString(json))
        val expected = ThomasEmailRegistrationOptions.DoubleOptIn(
            properties = null
        )
        assertEquals(fromJson,  expected)
    }

}