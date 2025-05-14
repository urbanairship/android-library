/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppFormDisplayEvent(
    override val data: ReportingEvent.FormDisplayData
) : InAppEvent {

    override val eventType: EventType = EventType.IN_APP_FORM_DISPLAY
}
