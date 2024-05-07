package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppPagerSummaryEventTest {
    @Test
    public fun testEvent() {
        val event = InAppPagerSummaryEvent(
            pagerData = PagerData("pager identifier", 3, "page identifier", 12, false),
            viewedPages = listOf(
                PageViewSummary("page 1", 0, 10),
                PageViewSummary("page 2", 1, 3),
                PageViewSummary("page 3", 2, 4),
            )
        )
        val expected = """
            {
               "viewed_pages":[
                  {
                     "display_time":10,
                     "page_identifier":"page 1",
                     "page_index":0
                  },
                  {
                     "page_index":1,
                     "display_time":3,
                     "page_identifier":"page 2"
                  },
                  {
                     "page_identifier":"page 3",
                     "page_index":2,
                     "display_time":4
                  }
               ],
               "page_count":12,
               "completed":false,
               "pager_identifier":"pager identifier"
            }
        """.trimIndent()

        assertEquals("in_app_pager_summary", event.name)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
