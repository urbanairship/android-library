/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * Exact value matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExactValueMatcher
/**
 * Default constructor.
 *
 * @param expected The expected value.
 */
public constructor(private val expected: JsonValue) : ValueMatcher() {

    override fun toJsonValue(): JsonValue  = jsonMapOf(
        EQUALS_VALUE_KEY to expected
    ).toJsonValue()

    protected override fun apply(value: JsonValue, ignoreCase: Boolean): Boolean {
        return if (ignoreCase) {
            equalsIgnoreCase(value, expected)
        } else {
            value == expected
        }
    }

    private fun equalsIgnoreCase(
        valueOne: JsonValue,
        valueTwo: JsonValue,
    ): Boolean {
        if (valueOne.isString  && valueTwo.isString) {
            return valueOne.optString().equals(valueTwo.optString(), ignoreCase = true)
        }

        if (valueOne.isJsonList && valueTwo.isJsonList) {
            val arrayOne = valueOne.optList()
            val arrayTwo = valueTwo.optList()

            if (arrayOne.size() != arrayTwo.size()) {
                return false
            }

            for (i in 0..<arrayOne.size()) {
                if (!equalsIgnoreCase(arrayOne[i], arrayTwo[i])) {
                    return false
                }
            }

            return true
        }

        if (valueOne.isJsonMap && valueTwo.isJsonMap) {
            val mapOne = valueOne.optMap()
            val mapTwo = valueTwo.optMap()

            if (mapOne.size() != mapTwo.size()) {
                return false
            }

            for ((key, childOne) in mapOne) {
                val childTwo = mapTwo[key] ?: return false
                if (!equalsIgnoreCase(childOne, childTwo)) {
                    return false
                }
            }

            return true
        }

        // Remaining types - bool, number
        return valueOne == valueTwo
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as ExactValueMatcher

        return expected == that.expected
    }

    override fun hashCode(): Int {
        return expected.hashCode()
    }

    public companion object {
        public const val EQUALS_VALUE_KEY: String = "equals"
    }
}
