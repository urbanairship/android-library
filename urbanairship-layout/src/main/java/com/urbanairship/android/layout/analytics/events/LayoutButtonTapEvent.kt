/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import androidx.annotation.VisibleForTesting
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

internal class LayoutButtonTapEvent @VisibleForTesting constructor(
    override val data: JsonSerializable
) : LayoutEvent {

    override val eventType: EventType = EventType.IN_APP_BUTTON_TAP

    constructor(info: ReportingEvent.ButtonTapData): this(data = info)
}
