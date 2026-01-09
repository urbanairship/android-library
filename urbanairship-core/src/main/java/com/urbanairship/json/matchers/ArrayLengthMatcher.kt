package com.urbanairship.json.matchers

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

internal class ArrayLengthMatcher(
    private val predicate: JsonPredicate
) : ValueMatcher() {

    companion object {
        const val ARRAY_LENGTH_KEY: String = "array_length"
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        ARRAY_LENGTH_KEY to predicate
    ).toJsonValue()

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        if (!jsonValue.isJsonList) {
            return false
        }

        val list = jsonValue.optList()
        return predicate.apply(JsonValue.wrap(list.size()))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as ArrayLengthMatcher
        return predicate == that.predicate
    }

    override fun hashCode(): Int {
        val result = 31 * predicate.hashCode()
        return result
    }
}
