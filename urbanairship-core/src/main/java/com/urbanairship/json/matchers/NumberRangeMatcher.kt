/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * Range matcher.
 *
 * @hide
 */
internal class NumberRangeMatcher(
    private val min: Double?,
    private val max: Double?
) : ValueMatcher() {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as NumberRangeMatcher

        if (min != that.min) { return false }
        if (max != that.max) { return false }

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(min, max)
    }

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        if (min == null && max == null) {
            return true
        }

        if (!jsonValue.isNumber) {
            return false
        }

        min?.let {
            if (jsonValue.getDouble(0.0) < it) {
                return false
            }
        }

        max?.let {
            if (jsonValue.getDouble(0.0) > it) {
                return false
            }
        }

        return true
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        MIN_VALUE_KEY to min,
        MAX_VALUE_KEY to max
    ).toJsonValue()

    companion object {
        const val MIN_VALUE_KEY: String = "at_least"
        const val MAX_VALUE_KEY: String = "at_most"
    }
}
