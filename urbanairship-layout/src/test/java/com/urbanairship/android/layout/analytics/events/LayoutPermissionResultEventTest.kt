package com.urbanairship.android.layout.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutPermissionResultEventTest {
    @Test
    public fun testEvent() {
        val event = LayoutPermissionResultEvent(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            startingStatus = PermissionStatus.DENIED,
            endingStatus = PermissionStatus.GRANTED
        )

        val expected = """
            {
               "permission":"display_notifications",
               "starting_permission_status":"denied",
               "ending_permission_status":"granted"
            }
        """.trimIndent()

        assertEquals("in_app_permission_result", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
