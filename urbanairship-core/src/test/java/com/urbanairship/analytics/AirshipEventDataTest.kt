/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import com.urbanairship.BaseTestCase
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

public class AirshipEventDataTest : BaseTestCase() {

    @Test
    public fun testCreateEventPayload() {
        val event = AirshipEventData(
            id = UUID.randomUUID().toString(),
            sessionId = UUID.randomUUID().toString(),
            timeMs = 1000L,
            body = jsonMapOf("foo" to "bar").toJsonValue(),
            type = EventType.SCREEN_TRACKING
        )

        val expectedData = """
            {
                "event_id": "${event.id}",
                "type": "${event.type.reportingName}",
                "time": "${Event.millisecondsToSecondsString(event.timeMs)}",
                "data": {
                    "session_id": "${event.sessionId}",
                    "foo": "bar"
                }
            }
        """.trimIndent()

        assertEquals(event.fullEventPayload, JsonValue.parseString(expectedData))
    }

}
