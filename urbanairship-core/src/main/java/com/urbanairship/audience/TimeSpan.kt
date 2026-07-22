/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField

/**
 * A time window with optional bounds, expressed in milliseconds since the epoch.
 *
 * A `null` start is treated as -∞ and a `null` end as +∞. The start bound is inclusive and
 * the end bound is exclusive (`start <= now < end`), matching iOS `AirshipTimeCriteria`.
 */
internal data class TimeSpan(
    val startTimestamp: Long?,
    val endTimestamp: Long?
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

    fun isActive(nowMs: Long): Boolean {
        if (startTimestamp != null && nowMs < startTimestamp) {
            return false
        }
        if (endTimestamp != null && nowMs >= endTimestamp) {
            return false
        }
        return true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_START to startTimestamp,
        KEY_END to endTimestamp
    ).toJsonValue()
}
