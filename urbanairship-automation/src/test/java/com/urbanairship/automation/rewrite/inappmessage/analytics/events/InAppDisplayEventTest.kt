package com.urbanairship.automation.rewrite.inappmessage.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppDisplayEventTest {
    @Test
    public fun testEvent() {
        val event = InAppDisplayEvent()
        assertEquals("in_app_display", event.name)
        assertNull(event.data)
    }
}