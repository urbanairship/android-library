/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable

internal class InAppDisplayEvent : InAppEvent {
    override val eventType: EventType = EventType.IN_APP_DISPLAY
    override val data: JsonSerializable? = null
}
