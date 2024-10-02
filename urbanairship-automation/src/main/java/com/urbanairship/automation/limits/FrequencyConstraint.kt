/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.automation.limits.storage.ConstraintEntity
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class FrequencyConstraint(
    val identifier: String,
    val range: Duration,
    val count: Int
) {
    companion object {
        private const val IDENTIFIER = "id"
        private const val RANGE = "range"
        private const val BOUNDARY = "boundary"
        private const val PERIOD = "period"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): FrequencyConstraint {
            val content = value.requireMap()
            val range = content.requireField<Long>(RANGE)
            val period = Period.fromJson(content.require(PERIOD))

            return FrequencyConstraint(
                identifier = content.requireField(IDENTIFIER),
                range = period.toSeconds(range),
                count = content.requireField<Int>(BOUNDARY)
            )
        }
    }

    private enum class Period(val json: String) {
        SECONDS("seconds"),
        MINUTES("minutes"),
        HOURS("hours"),
        DAYS("days"),
        WEEKS("weeks"),
        MONTHS("months"),
        YEARS("years");

        companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Period {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid period $content")
            }
        }

        fun toSeconds(value: Long): Duration {
            return when(this) {
                SECONDS -> value
                MINUTES -> value * 60
                HOURS -> value * 60 * 60
                DAYS -> value * 60 * 60 * 24
                WEEKS -> value * 60 * 60 * 24 * 7
                MONTHS -> value * 60 * 60 * 24 * 30
                YEARS -> value * 60 * 60 * 24 * 365
            }.seconds
        }
    }

    internal fun makeEntity(): ConstraintEntity {
        val result = ConstraintEntity()
        result.constraintId = identifier
        result.range = range
        result.count = count
        return result
    }
}
