/* Copyright Airship and Contributors */

package com.urbanairship.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonValue
import com.urbanairship.json.extend
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.FormatterUtils.toSecondsString
import java.time.Instant

/**
 * Airship event data.
 */
public class AirshipEventData(
    /**
     * Event Id
     */
    public val id: String,

    /**
     * Session Id
     */
    public val sessionId: String,

    /**
     * Event body
     */
    public var body: JsonValue,

    /**
     * Event type
     */
    public val type: EventType,

    /**
     * The time the event occurred.
     */
    public val timestamp: Instant
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipEventData

        if (id != other.id) return false
        if (body != other.body) return false
        if (type != other.type) return false
        if (timestamp != other.timestamp) return false


        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    override fun toString(): String {
        return "AirshipEventData(id='$id', sessionId='$sessionId', body=$body, type=$type, timestamp=$timestamp)"
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val fullEventPayload: JsonValue = jsonMapOf(
        Event.TYPE_KEY to type.reportingName,
        Event.EVENT_ID_KEY to id,
        Event.TIME_KEY to timestamp.toSecondsString(),
        Event.DATA_KEY to body.optMap().extend(
            Event.SESSION_ID_KEY to sessionId
        )
    ).toJsonValue()
}
