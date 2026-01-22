/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import androidx.annotation.RestrictTo
import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InAppDisplayEvent : LayoutEvent {
    override val eventType: EventType = EventType.IN_APP_DISPLAY
    override val data: JsonSerializable? = null
}
