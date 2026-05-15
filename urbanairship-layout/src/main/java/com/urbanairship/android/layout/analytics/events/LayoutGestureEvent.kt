/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import androidx.annotation.VisibleForTesting
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

internal class LayoutGestureEvent @VisibleForTesting constructor(
    override val data: JsonSerializable
) : LayoutEvent {

    override val eventType: EventType = EventType.LAYOUT_GESTURE

    constructor(eventData: ReportingEvent.GestureData): this(data = eventData)
}
