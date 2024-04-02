package com.urbanairship.automation.rewrite.inappmessage.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppPageActionEventTest {
    @Test
    public fun testEvent() {
        val event = InAppPageActionEvent(
            identifier = "action id",
            metadata = JsonValue.wrap("reporting metadata")
        )

        val expected = """
            {
               "reporting_metadata":"reporting metadata",
               "action_identifier":"action id"
            }
        """.trimIndent()
        assertEquals("in_app_page_action", event.name)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
