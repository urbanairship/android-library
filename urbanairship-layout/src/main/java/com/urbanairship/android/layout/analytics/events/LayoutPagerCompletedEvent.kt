/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent

internal class LayoutPagerCompletedEvent(
    override val data: ReportingEvent.PagerCompleteData
) : LayoutEvent {

    override val eventType: EventType = EventType.LAYOUT_PAGER_COMPLETED
}
