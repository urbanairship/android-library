/* Copyright Airship and Contributors */

package com.urbanairship.debug.event.persistence

import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.urbanairship.analytics.AirshipEventData
import com.urbanairship.analytics.Event

/**
 * Entities stored in the event database.\
 * @hide
 */
@Entity(tableName = "events")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val eventId: String,
    val session: String,
    val payload: String,
    val time: Long,
    val type: String
) {

    constructor(event: AirshipEventData) : this(
        id = 0,
        eventId = event.id,
        session = event.sessionId,
        payload = event.body.toString(),
        time = event.timeMs,
        type = event.type.reportingName
    )
}
