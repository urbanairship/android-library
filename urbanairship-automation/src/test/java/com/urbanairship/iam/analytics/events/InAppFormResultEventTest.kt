package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppFormResultEventTest {
    @Test
    public fun testEvent() {
        val event = InAppFormResultEvent(forms = JsonValue.wrap("form result"))
        val expected = """
            {
               "forms": "form result"
            }
        """.trimIndent()

        assertEquals("in_app_form_result", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
