/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalEpochMillis
import java.time.Instant

/**
 * A time window with optional bounds.
 *
 * A `null` start is treated as -∞ and a `null` end as +∞. The start bound is inclusive and
 * the end bound is exclusive (`start <= now < end`).
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class TimeSpan(
    public val startTimestamp: Instant?,
    public val endTimestamp: Instant?
) : JsonSerializable {

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val KEY_START = "start_timestamp"
        private const val KEY_END = "end_timestamp"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonMap): TimeSpan = TimeSpan(
            startTimestamp = json.optionalEpochMillis(KEY_START),
            endTimestamp = json.optionalEpochMillis(KEY_END)
        )
    }

    public fun isActive(now: Instant): Boolean {
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
