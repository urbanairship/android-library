/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalEpochMillis
import java.time.Instant

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public data class TimeCriteria(
    private val start: Instant?,
    private val end: Instant?,
) : JsonSerializable {

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val KEY_START = "start_timestamp"
        private const val KEY_END = "end_timestamp"

        public fun fromJson(json: JsonMap?): TimeCriteria? {
            val content = json ?: return null

            return TimeCriteria(
                start = content.optionalEpochMillis(KEY_START),
                end = content.optionalEpochMillis(KEY_END))
        }
    }

    public fun meets(date: Instant): Boolean {
        val meetsStart = start?.let { it <= date } ?: true
        val meetsEnd = end?.let { it >= date } ?: true
        return meetsStart && meetsEnd
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_START to start?.toEpochMilli(),
            KEY_END to end?.toEpochMilli()
        ).toJsonValue()
    }
}
