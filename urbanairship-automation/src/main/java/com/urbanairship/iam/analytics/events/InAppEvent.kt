/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import androidx.annotation.RestrictTo
import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface InAppEvent {
    public val eventType: EventType
    public val data:  JsonSerializable?
}
