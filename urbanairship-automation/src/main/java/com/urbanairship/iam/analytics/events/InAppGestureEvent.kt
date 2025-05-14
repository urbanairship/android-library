/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import androidx.annotation.VisibleForTesting
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppGestureEvent @VisibleForTesting constructor(
    override val data: JsonSerializable
) : InAppEvent {

    override val eventType: EventType = EventType.IN_APP_GESTURE

    constructor(eventData: ReportingEvent.GestureData): this(data = eventData)
}
