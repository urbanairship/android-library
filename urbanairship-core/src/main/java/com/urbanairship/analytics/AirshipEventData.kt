package com.urbanairship.analytics

import com.urbanairship.json.JsonValue

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
    public val type: String,

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
}
