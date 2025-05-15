/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent

internal class InAppPagerCompletedEvent(
    override val data: ReportingEvent.PagerCompleteData
) : InAppEvent {

    override val eventType: EventType = EventType.IN_APP_PAGER_COMPLETED
}
