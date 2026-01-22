package com.urbanairship.android.layout.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutDisplayEventTest {
    @Test
    public fun testEvent() {
        val event = InAppDisplayEvent()
        assertEquals("in_app_display", event.eventType.reportingName)
        assertNull(event.data)
    }
}
