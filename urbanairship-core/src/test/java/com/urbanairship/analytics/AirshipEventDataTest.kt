/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import com.urbanairship.BaseTestCase
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.FormatterUtils.toSecondsString
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test

public class AirshipEventDataTest : BaseTestCase() {

    @Test
    public fun testCreateEventPayload() {
        val event = AirshipEventData(
            id = UUID.randomUUID().toString(),
            sessionId = UUID.randomUUID().toString(),
            timestamp = Instant.ofEpochMilli(1000),
            body = jsonMapOf("foo" to "bar").toJsonValue(),
            type = EventType.SCREEN_TRACKING
        )

        val expectedData = """
            {
                "event_id": "${event.id}",
                "type": "${event.type.reportingName}",
                "time": "${event.timestamp.toSecondsString()}",
                "data": {
                    "session_id": "${event.sessionId}",
                    "foo": "bar"
                }
            }
        """.trimIndent()

        assertEquals(event.fullEventPayload, JsonValue.parseString(expectedData))
    }

}
