/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * String begins with value matcher.
 */
public class StringBeginsMatcher(private val expected: JsonValue): ValueMatcher() {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        STRING_BEGINS to expected
    ).toJsonValue()

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        val stringValue = jsonValue.string ?: return false
        val prefix = expected.string ?: return false
        return stringValue.startsWith(prefix, ignoreCase)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringBeginsMatcher

        return expected == other.expected
    }

    override fun hashCode(): Int {
        return expected.hashCode()
    }

    internal companion object {
        const val STRING_BEGINS: String = "string_begins"
    }
}
