/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent

internal class LayoutFormDisplayEvent(
    override val data: ReportingEvent.FormDisplayData
) : LayoutEvent {

    override val eventType: EventType = EventType.LAYOUT_FORM_DISPLAY
}
