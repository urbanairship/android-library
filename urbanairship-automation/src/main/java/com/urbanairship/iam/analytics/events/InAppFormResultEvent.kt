/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import androidx.annotation.VisibleForTesting
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

internal class InAppFormResultEvent @VisibleForTesting constructor (
    override val data: JsonSerializable
) : InAppEvent {

    override val eventType: EventType = EventType.IN_APP_FORM_RESULT

    constructor(formData: ReportingEvent.FormResultData) : this(data = formData)
}
