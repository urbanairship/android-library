/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent

internal class InAppFormDisplayEvent(
    override val data: ReportingEvent.FormDisplayData
) : InAppEvent {

    override val eventType: EventType = EventType.IN_APP_FORM_DISPLAY
}
