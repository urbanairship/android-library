/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppPagerCompletedEvent(
    override val data: ReportingEvent.PagerCompleteData
) : InAppEvent {

    override val eventType: EventType = EventType.IN_APP_PAGER_COMPLETED
}
