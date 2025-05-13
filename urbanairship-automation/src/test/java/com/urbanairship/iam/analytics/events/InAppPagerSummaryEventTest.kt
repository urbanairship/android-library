package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppPagerSummaryEventTest {
    @Test
    public fun testEvent() {
        val event = InAppPagerSummaryEvent(
            data = ReportingEvent.PageSummaryData(
                identifier = "pager identifier",
                pageCount = 12,
                completed = false,
                viewedPages = listOf(
                    ReportingEvent.PageSummaryData.PageView(
                        identifier = "page 1",
                        index = 0,
                        displayTime = 10.seconds + 400.milliseconds),
                    ReportingEvent.PageSummaryData.PageView(
                        identifier = "page 2",
                        index = 1,
                        displayTime = 3.seconds),
                    ReportingEvent.PageSummaryData.PageView(
                        identifier = "page 3",
                        index = 2,
                        displayTime = 4.seconds),
                )
            ),
        )
        val expected = """
            {
               "viewed_pages":[
                  {
                     "display_time":"10.40",
                     "page_identifier":"page 1",
                     "page_index":0
                  },
                  {
                     "page_index":1,
                     "display_time":"3.00",
                     "page_identifier":"page 2"
                  },
                  {
                     "page_identifier":"page 3",
                     "page_index":2,
                     "display_time":"4.00"
                  }
               ],
               "page_count":12,
               "completed":false,
               "pager_identifier":"pager identifier"
            }
        """.trimIndent()

        assertEquals("in_app_pager_summary", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
