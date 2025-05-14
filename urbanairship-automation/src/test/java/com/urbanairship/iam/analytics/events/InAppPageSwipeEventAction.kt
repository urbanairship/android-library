package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppPageSwipeEventAction {
    @Test
    public fun testEvent() {
        val event = InAppPageSwipeEvent(
            data = ReportingEvent.PageSwipeData(
                identifier = "pager identifier",
                toPageIndex = 4,
                toPageIdentifier = "to page identifier",
                fromPageIndex = 3,
                fromPageIdentifier = "from page identifier"
            ),
        )

        val expected = """
            {
               "pager_identifier":"pager identifier",
               "from_page_index":3,
               "to_page_identifier":"to page identifier",
               "from_page_identifier":"from page identifier",
               "to_page_index":4
            }
        """.trimIndent()

        assertEquals("in_app_page_swipe", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
