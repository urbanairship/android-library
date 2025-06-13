/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers

import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * String contains with value matcher.
 */
public class StringContainsMatcher(internal val expected: JsonValue): ValueMatcher() {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        STRING_CONTAINS to expected
    ).toJsonValue()

    override fun apply(value: JsonValue, ignoreCase: Boolean): Boolean {
        val stringValue = value.string ?: return false
        val contains = expected.string ?: return false
        return stringValue.contains(contains, ignoreCase)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringContainsMatcher

        return expected == other.expected
    }

    override fun hashCode(): Int {
        return expected.hashCode()
    }

    internal companion object {
        const val STRING_CONTAINS: String = "string_contains"
    }
}
