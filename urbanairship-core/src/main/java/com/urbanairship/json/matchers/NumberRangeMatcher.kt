/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf
import com.google.android.gms.common.internal.Objects

/**
 * Range matcher.
 *
 * @hide
 */
internal class NumberRangeMatcher(
    private val min: Double?,
    private val max: Double?
) : ValueMatcher() {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as NumberRangeMatcher

        if (min != that.min) { return false }
        if (max != that.max) { return false }

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(min, max)
    }

    override fun apply(value: JsonValue, ignoreCase: Boolean): Boolean {
        if (min == null && max == null) {
            return true
        }

        if (!value.isNumber) {
            return false
        }

        min?.let {
            if (value.getDouble(0.0) < it) {
                return false
            }
        }

        max?.let {
            if (value.getDouble(0.0) > it) {
                return false
            }
        }

        return true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        MIN_VALUE_KEY to min,
        MAX_VALUE_KEY to max
    ).toJsonValue()

    companion object {
        const val MIN_VALUE_KEY: String = "at_least"
        const val MAX_VALUE_KEY: String = "at_most"
    }
}
