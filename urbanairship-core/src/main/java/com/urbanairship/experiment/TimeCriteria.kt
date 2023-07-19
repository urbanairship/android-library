package com.urbanairship.experiment

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class TimeCriteria(
    private val start: Long?,
    private val end: Long?,
) : JsonSerializable {

    public companion object {
        private const val KEY_START = "start_timestamp"
        private const val KEY_END = "end_timestamp"

        public fun fromJson(json: JsonMap?): TimeCriteria? {
            val content = json ?: return null

            return TimeCriteria(
                start = content.optionalField(KEY_START),
                end = content.optionalField(KEY_END))
        }
    }

    public fun meets(date: Long): Boolean {
        val meetsStart = start?.let { it < date } ?: true
        val meetsEnd = end?.let { it >= date } ?: true
        return meetsStart && meetsEnd
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_START to start,
            KEY_END to end
        ).toJsonValue()
    }
}
