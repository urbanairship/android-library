/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import java.time.Instant

/**
 * A time window with optional bounds, expressed in milliseconds since the epoch.
 *
 * A `null` start is treated as -∞ and a `null` end as +∞. The start bound is inclusive and
 * the end bound is exclusive (`start <= now < end`).
 */
internal data class TimeSpan(
    val startTimestamp: Instant?,
    val endTimestamp: Instant?
) : JsonSerializable {

    companion object {
        private const val KEY_START = "start_timestamp"
        private const val KEY_END = "end_timestamp"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): TimeSpan = TimeSpan(
            startTimestamp = json.optionalField(KEY_START),
            endTimestamp = json.optionalField(KEY_END)
        )
    }

    fun isActive(now: Instant): Boolean {
        if (startTimestamp != null && now < startTimestamp) {
            return false
        }
        if (endTimestamp != null && now >= endTimestamp) {
            return false
        }
        return true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_START to startTimestamp?.toEpochMilli(),
        KEY_END to endTimestamp?.toEpochMilli()
    ).toJsonValue()
}
