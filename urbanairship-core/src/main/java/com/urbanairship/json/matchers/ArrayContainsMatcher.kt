/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * Array contains matcher.
 *
 * @hide
 */
internal class ArrayContainsMatcher(
    private val predicate: JsonPredicate,
    private val index: Int?
) : ValueMatcher() {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        ARRAY_CONTAINS_KEY to predicate,
        INDEX_KEY to index
    ).toJsonValue()

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        if (!jsonValue.isJsonList) {
            return false
        }

        val list = jsonValue.optList()

        if (index != null) {
            if (index < 0 || index >= list.size()) {
                return false
            }

            return predicate.apply(list[index])
        }

        return list.any { predicate.apply(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as ArrayContainsMatcher

        if (index != that.index) { return false }
        return predicate == that.predicate
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(index, predicate)
    }

    companion object {
        /**
         * Json key for the predicate.
         */
        const val ARRAY_CONTAINS_KEY: String = "array_contains"

        /**
         * Json key for the index.
         */
        const val INDEX_KEY: String = "index"
    }
}
