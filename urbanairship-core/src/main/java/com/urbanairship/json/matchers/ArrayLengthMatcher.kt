package com.urbanairship.json.matchers

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

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ArrayLengthMatcher
        return predicate == that.predicate
    }

    override fun hashCode(): Int {
        val result = 31 * predicate.hashCode()
        return result
    }
}
