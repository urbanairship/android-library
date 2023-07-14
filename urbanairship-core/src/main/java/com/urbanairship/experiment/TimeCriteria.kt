package com.urbanairship.experiment

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

internal class TimeCriteria(
    val start: Long?,
    val end: Long?,
) {

    companion object {
        private const val KEY_START = "start_timestamp"
        private const val KEY_END = "end_timestamp"

        internal fun fromJson(json: JsonMap?): TimeCriteria? {
            val content = json ?: return null

            return TimeCriteria(
                start = content.optionalField(KEY_START),
                end = content.optionalField(KEY_END))
        }
    }

    fun meets(date: Long): Boolean {
        val meetsStart = start?.let { it < date } ?: true
        val meetsEnd = end?.let { it >= date } ?: true
        return meetsStart && meetsEnd
    }
}
