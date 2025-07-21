/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.Predicate

/**
 * Class representing the leaf node of a JsonPredicate that contains the relevant field matching info.
 */
public class JsonMatcher private constructor(
    private val key: String?,
    private val scopeList: List<String>,
    private val value: ValueMatcher,
    private val ignoreCase: Boolean?
) : JsonSerializable, Predicate<JsonSerializable> {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        FIELD_KEY to key,
        SCOPE_KEY to scopeList,
        VALUE_KEY to value.toJsonValue(),
        IGNORE_CASE_KEY to ignoreCase
    ).toJsonValue()

    override fun apply(value: JsonSerializable): Boolean {
        var jsonValue = value.toJsonValue()
        for (scope in scopeList) {
            jsonValue = jsonValue.optMap().opt(scope)
            if (jsonValue.isNull) {
                break
            }
        }

        if (key != null) {
            jsonValue = jsonValue.optMap().opt(key)
        }

        return this.value.apply(jsonValue, (ignoreCase != null) && ignoreCase)
    }

    /**
     * Builder class.
     */
    public class Builder {

        public var valueMatcher: ValueMatcher? = null
            private set

        public var scope: List<String> = listOf()
            private set

        internal var key: String? = null

        public var ignoreCase: Boolean? = null
            private set

        /**
         * Sets the [ValueMatcher].
         *
         * @param valueMatcher The [ValueMatcher] instance.
         * @return The [Builder] instance.
         */
        public fun setValueMatcher(valueMatcher: ValueMatcher?): Builder {
            return this.also { it.valueMatcher = valueMatcher }
        }

        /**
         * Sets the scope.
         *
         * @param scope The scope as a list of fields.
         * @return The [Builder] instance.
         */
        public fun setScope(scope: List<String>?): Builder {
            return this.also { it.scope = scope?.toList() ?: emptyList() }
        }

        /**
         * Sets the scope.
         *
         * @param scope The scope as a single field.
         * @return The [Builder] instance.
         */
        public fun setScope(scope: String): Builder {
            return this.also { it.scope = listOf(scope) }
        }

        /**
         * Sets the key.
         *
         * @param key The key.
         * @return The [Builder] instance.
         */
        public fun setKey(key: String?): Builder {
            return this.also { it.key = key }
        }

        /**
         * Sets ignoreCase.
         *
         * @param ignoreCase The ignoreCase flag.
         * @return The [Builder] instance.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun setIgnoreCase(ignoreCase: Boolean): Builder {
            return this.also { it.ignoreCase = ignoreCase }
        }

        /**
         * Builds the [JsonMatcher] instance.
         *
         * @return The [JsonMatcher] instance.
         */
        public fun build(): JsonMatcher {
            return JsonMatcher(
                key = key,
                scopeList = scope,
                value = valueMatcher ?: ValueMatcher.newIsPresentMatcher(),
                ignoreCase = ignoreCase
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val matcher = other as JsonMatcher

        if (key != matcher.key) { return false }
        if (scopeList != matcher.scopeList) { return false }
        if (ignoreCase != matcher.ignoreCase) { return false }

        return value == matcher.value
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(key, scopeList, value, ignoreCase)
    }

    public companion object {

        private const val VALUE_KEY = "value"
        private const val FIELD_KEY = "key"
        private const val SCOPE_KEY = "scope"
        private const val IGNORE_CASE_KEY = "ignore_case"

        /**
         * Parses a [JsonValue] object into a [JsonMatcher].
         *
         * @param jsonValue The predicate as a [JsonValue].
         * @return The parsed [JsonMatcher].
         * @throws JsonException If the JSON is invalid.
         */
        @JvmStatic
        @Throws(JsonException::class)
        public fun parse(jsonValue: JsonValue?): JsonMatcher {
            if (jsonValue == null || !jsonValue.isJsonMap || jsonValue.optMap().isEmpty) {
                throw JsonException("Unable to parse empty JsonValue: $jsonValue")
            }

            val map = jsonValue.optMap()

            val value = map[VALUE_KEY]
                ?: throw JsonException("JsonMatcher must contain a value matcher.")

            val builder = newBuilder()
                .setKey(map.opt(FIELD_KEY).string)
                .setValueMatcher(ValueMatcher.parse(value))

            val scope = map.opt(SCOPE_KEY)
            if (scope.isString) {
                builder.setScope(scope.optString())
            } else if (scope.isJsonList) {
                val scopeList = scope.optList().list.mapNotNull { it.string }
                builder.setScope(scopeList)
            }

            map[IGNORE_CASE_KEY]?.let { builder.setIgnoreCase(it.getBoolean(false)) }

            return builder.build()
        }

        /**
         * Builder factory method.
         *
         * @return A new builder instance.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }
    }
}
