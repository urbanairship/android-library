/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * String ends with value matcher.
 */
public class StringEndsMatcher(private val expected: JsonValue): ValueMatcher() {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        STRING_ENDS to expected
    ).toJsonValue()

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        val stringValue = jsonValue.string ?: return false
        val suffix = expected.string ?: return false
        return stringValue.endsWith(suffix, ignoreCase)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringEndsMatcher

        return expected == other.expected
    }

    override fun hashCode(): Int {
        return expected.hashCode()
    }

    internal companion object {
        const val STRING_ENDS: String = "string_ends"
    }
}
