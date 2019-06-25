/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import androidx.annotation.RestrictTo
import com.urbanairship.debug.event.persistence.EventEntity
import com.urbanairship.json.JsonValue

/***
 * Event item used in the event adapter.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EventItem(val eventEntity: EventEntity) {

    val payload by lazy {
        JsonValue.parseString(eventEntity.payload).optMap().opt("data").optMap()
    }

    val type = eventEntity.type

    val time = eventEntity.time

    val id: String = eventEntity.eventId
}
