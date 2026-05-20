package com.urbanairship.android.layout.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutGestureTapEventTest {
    @Test
    public fun testEvent() {
        val event = InAppGestureEvent(
            data = ReportingEvent.GestureData(
                identifier = "gesture id", reportingMetadata = JsonValue.wrap("reporting metadata")
            ),
        )

        val expected = """
            {
               "reporting_metadata":"reporting metadata",
               "gesture_identifier":"gesture id"
            }
        """.trimIndent()

        assertEquals("in_app_gesture", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
