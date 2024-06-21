/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.urbanairship.analytics.AirshipEventData
import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable

/**
 * Entities stored in the event database.\
 * @hide
 */
@Entity(tableName = "events")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val eventId: String,
    val session: String,
    val payload: String,
    val time: Long,
    val type: EventType
) {

    constructor(event: AirshipEventData) : this(
        id = 0,
        eventId = event.id,
        session = event.sessionId,
        payload = event.body.toString(),
        time = event.timeMs,
        type = event.type
    )

    internal fun toJson() : JsonSerializable {
        return JsonMap.newBuilder()
            .put("id", this.id)
            .put("eventId", this.eventId)
            .put("session", this.session)
            .put("payload", this.payload)
            .put("time", this.time)
            .put("type", this.type.toString())
            .build()
            .toJsonValue()
    }
}