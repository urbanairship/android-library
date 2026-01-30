/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DateUtils
import java.util.Date
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageTest {

    /** Test message parses its data correctly. */
    @Test
    @Throws(JsonException::class)
    public fun testMessage() {
        val message = requireNotNull(
            Message.create(JsonValue.parseString(MCRAP_MESSAGE), true, false)
        )
        assertEquals("MESSAGE_ID", message.id)
        assertEquals("MESSAGE_TITLE", message.title)
        assertEquals(
            "https://dl.urbanairship.com/binary/token/app/MESSAGE_ID/body/",
            message.bodyUrl
        )
        assertEquals(
            "https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/",
            message.messageUrl
        )
        assertEquals(Date(1443026786000L), message.sentDate)
        assertFalse(message.isRead)
        assertFalse(message.isDeleted)

        // Extras
        assertEquals(1, message.extras?.size)
        assertEquals("some_value", message.extras?.get("some_key"))

        // Expiry
        assertNull(message.expirationDate)
        assertFalse(message.isExpired)

        // Raw message JSON
        assertEquals(JsonValue.parseString(MCRAP_MESSAGE).toString(true), message.rawMessageJson.toString(true))
    }

    /** Test message parses its data correctly. */
    @Test
    @Throws(JsonException::class)
    public fun testMessageExpiry() {
        // Add expiry
        val map = JsonValue.parseString(MCRAP_MESSAGE).requireMap().map.toMutableMap()
        map["message_expiry"] = JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L))
        val message = requireNotNull(
            Message.create(JsonValue.wrap(map), true, false)
        )

        // Expiry
        assertEquals(Date(10000L), message.expirationDate)
        assertTrue(message.isExpired)
    }


    /** Test message parses its data correctly. */
    @Test
    @Throws(JsonException::class)
    public fun testMessageMissingSentDate() {
        val map = JsonValue.parseString(MCRAP_MESSAGE).requireMap().map.toMutableMap()

        // remove sent date
        map.remove("message_sent")

        val now = Date()
        val message = requireNotNull(
            Message.create(JsonValue.wrap(map), true, false)
        )

        // Verify that we set the sent date to now when the message was created
        assertEquals(now.toString(), message.sentDate.toString())
    }

    @Test
    public fun testContentTypeParsing() {
        // A map of valid content_type string values to their expected Message.ContentType
        val validCases = mapOf(
            "text/html" to Message.ContentType.Html,
            "text/plain" to Message.ContentType.Plain,
            "application/vnd.urbanairship.thomas+json;version=1" to Message.ContentType.Native(1),
            "application/vnd.urbanairship.thomas+json; version=2" to Message.ContentType.Native(2),
            "application/vnd.urbanairship.thomas+json; version=3; foo=bar" to Message.ContentType.Native(3),
        )

        validCases.forEach { (typeString, expectedType) ->
            val type = Message.ContentType.fromJson(JsonValue.wrap(typeString))
            assertEquals("Failed for type: $typeString", expectedType, type)
        }

        // A list of invalid JSON value types for the content_type field.
        // The create method should handle these gracefully by defaulting to HTML, not throwing an exception.
        val invalidJsonTypeCases = listOf(
            100,
            false,
            null,
            mapOf("foo" to "bar"),
            listOf("foo", "bar"),
            "application/vnd.urbanairship.thomas+json",
            "application/vnd.urbanairship.thomas+json; version=foo",
            "text/html1",
            "application/vnd.urbanairship.thomas+json; garbage version=foo",
        ).map(JsonValue::wrap)

        invalidJsonTypeCases.forEach { invalidValue ->
            assertThrows(JsonException::class.java, {
                Message.ContentType.fromJson(invalidValue)
            })
        }
    }


    private companion object {
        @Language("JSON")
        private val MCRAP_MESSAGE = """
        {
            "content_size": 44,
            "message_url": "https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/",
            "title": "MESSAGE_TITLE",
            "message_sent": "2015-09-23 16:46:26",
            "options": {
              "asset_hosted": "true"
            },
            "message_id": "MESSAGE_ID",
            "message_body_url": "https://dl.urbanairship.com/binary/token/app/MESSAGE_ID/body/",
            "message_read_url": "https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/read/",
            "unread": false,
            "content_type": "text/html",
            "extra": {
                "some_key": "some_value"
            }
        }
        """.trimIndent()
    }
}
