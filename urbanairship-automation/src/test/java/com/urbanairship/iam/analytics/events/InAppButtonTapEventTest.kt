package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppButtonTapEventTest {
    @Test
    public fun testEvent() {
        val event = InAppButtonTapEvent(
            data = ReportingEvent.ButtonTapData(
                identifier = "button id",
                reportingMetadata = JsonValue.wrap("reporting metadata")
            )
        )

        val expected = """
            {
               "reporting_metadata":"reporting metadata",
               "button_identifier":"button id"
            }
        """.trimIndent()

        assertEquals("in_app_button_tap", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
