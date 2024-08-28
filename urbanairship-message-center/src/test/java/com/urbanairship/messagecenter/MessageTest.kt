/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DateUtils
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageTest {

    /** Test message parses its data correctly. */
    @Test
    @Throws(JsonException::class)
    public fun testMessage() {
        val message = Message.create(JsonValue.parseString(MCRAP_MESSAGE), true, false)
        assertEquals("MESSAGE_ID", message!!.messageId)
        assertEquals("MESSAGE_TITLE", message.title)
        assertEquals(
            "https://dl.urbanairship.com/binary/token/app/MESSAGE_ID/body/",
            message.messageBodyUrl
        )
        assertEquals(
            "https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/read/",
            message.messageReadUrl
        )
        assertEquals(
            "https://device-api.urbanairship.com/api/user/test/messages/message/MESSAGE_ID/",
            message.messageUrl
        )
        assertEquals(1443026786000L, message.sentDateMS)
        assertFalse(message.isRead)
        assertFalse(message.isDeleted)

        // Extras
        assertEquals(1, message.extras.size())
        assertEquals("some_value", message.extras.getString("some_key"))
        assertEquals("some_value", message.extrasMap["some_key"])

        // Expiry
        assertNull(message.expirationDate)
        assertNull(message.expirationDateMS)
        assertFalse(message.isExpired)

        // Raw message JSON
        assertEquals(JsonValue.parseString(MCRAP_MESSAGE), message.rawMessageJson)
    }

    /** Test message parses its data correctly. */
    @Test
    @Throws(JsonException::class)
    public fun testMessageExpiry() {
        // Add expiry
        val map = JsonValue.parseString(MCRAP_MESSAGE).requireMap().map
        map["message_expiry"] = JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L))
        val message = Message.create(JsonValue.wrap(map), true, false)

        // Expiry
        assertEquals(10000L, message!!.expirationDateMS)
        assertTrue(message.isExpired)
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
