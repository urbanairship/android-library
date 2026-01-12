package com.urbanairship.android.layout.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutFormDisplayEventTest {
    @Test
    public fun testEvent() {
        val event = InAppFormDisplayEvent(
            data = ReportingEvent.FormDisplayData(
                identifier = "form id", formType = "nps", responseType = "user feedback"
            )
        )

        val expected = """
            {
               "form_identifier":"form id",
               "form_type":"nps",
               "form_response_type":"user feedback"
            }
        """.trimIndent()

        assertEquals("in_app_form_display", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
