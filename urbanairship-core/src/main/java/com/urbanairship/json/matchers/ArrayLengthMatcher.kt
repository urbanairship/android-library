package com.urbanairship.json.matchers

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ArrayLengthMatcher(
    private val predicate: JsonPredicate
) : ValueMatcher() {

    public companion object {
        public const val ARRAY_LENGTH_KEY: String = "array_length"
    }

    override fun toJsonValue(): JsonValue {
        return JsonMap.newBuilder()
            .putOpt(ARRAY_LENGTH_KEY, predicate)
            .build()
            .toJsonValue()
    }

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
