package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppPageViewEventTest {
    @Test
    public fun testEvent() {
        val event = InAppPageViewEvent(
            pagerData = PagerData("pager identifier", 3, "page identifier", 12, false),
            viewCount = 31
        )

        val expected = """
            {
               "page_identifier":"page identifier",
               "page_index":3,
               "viewed_count":31,
               "page_count":12,
               "pager_identifier":"pager identifier",
               "completed":false
            }
        """.trimIndent()

        assertEquals("in_app_page_view", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
