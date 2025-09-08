package com.urbanairship.analytics.data

import androidx.core.util.ObjectsCompat
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.urbanairship.analytics.AirshipEventData
import com.urbanairship.analytics.Event
import com.urbanairship.json.JsonValue
import com.urbanairship.util.FormatterUtils.toSecondsString
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds

/**
 * Representation of an [Event] for persistent storage via Room.
 */
@Entity(tableName = "events", indices = [Index(value = ["eventId"], unique = true)])
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val eventId: String,
    val time: String,
    val data: JsonValue,
    val sessionId: String?,
    val eventSize: Int
) {

    constructor(event: AirshipEventData) : this(
        0,
        event.type.reportingName,
        event.id,
        event.timeMs.milliseconds.toSecondsString(),
        event.fullEventPayload,
        event.sessionId,
        event.fullEventPayload.toString().toByteArray(StandardCharsets.UTF_8).size
    )

    fun contentEquals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val entity = o as EventEntity
        return eventSize == entity.eventSize && ObjectsCompat.equals(
            type, entity.type
        ) && ObjectsCompat.equals(eventId, entity.eventId) && ObjectsCompat.equals(
            time, entity.time
        ) && ObjectsCompat.equals(data, entity.data) && ObjectsCompat.equals(
            sessionId, entity.sessionId
        )
    }

    /**
     * Minimal wrapper for queries that only need to return the event ID and data fields.
     */
    class EventIdAndData(
        val id: Int,
        val eventId: String,
        val data: JsonValue
    )
}
