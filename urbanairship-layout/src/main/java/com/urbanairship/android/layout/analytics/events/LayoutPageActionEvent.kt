/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import androidx.annotation.VisibleForTesting
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

internal class LayoutPageActionEvent @VisibleForTesting constructor(
    override val data: JsonSerializable
) : LayoutEvent {

    override val eventType: EventType = EventType.LAYOUT_PAGE_ACTION

    constructor(eventData: ReportingEvent.PageActionData): this(data = eventData)
}
