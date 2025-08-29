/* Copyright Airship and Contributors */

package com.urbanairship.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonValue
import com.urbanairship.json.extend
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.FormatterUtils.toSecondsString
import kotlin.time.Duration.Companion.milliseconds

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
     * Time in milliseconds.
     */
    public val timeMs: Long
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipEventData

        if (id != other.id) return false
        if (body != other.body) return false
        if (type != other.type) return false
        if (timeMs != other.timeMs) return false


        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + timeMs.hashCode()
        return result
    }

    override fun toString(): String {
        return "AirshipEventData(id='$id', sessionId='$sessionId', body=$body, type=$type, timeMs=$timeMs)"
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val fullEventPayload: JsonValue = jsonMapOf(
        Event.TYPE_KEY to type.reportingName,
        Event.EVENT_ID_KEY to id,
        Event.TIME_KEY to timeMs.milliseconds.toSecondsString(),
        Event.DATA_KEY to body.optMap().extend(
            Event.SESSION_ID_KEY to sessionId
        )
    ).toJsonValue()
}
