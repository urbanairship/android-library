package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppPagerCompletedEventTest {
    @Test
    public fun testEvent() {
        val event = InAppPagerCompletedEvent(
            data = ReportingEvent.PagerCompleteData(
                identifier = "pager identifier",
                pageIndex = 3,
                pageCount = 12,
                pageIdentifier = "page identifier"
            )
        )
        val expected = """
            {
               "page_count":12,
               "pager_identifier":"pager identifier",
               "page_index":3,
               "page_identifier":"page identifier"
            }
        """.trimIndent()
        assertEquals("in_app_pager_completed", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
