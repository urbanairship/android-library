/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.core.util.ObjectsCompat
import com.urbanairship.Predicate

/**
 * Class abstracting a JSON predicate. The predicate is contained to the following schema:
 *
 * <predicate>         := <json_matcher> | <not> | <and> | <or>
 * <not>               := { "not": { <predicate> } }
 * <and>               := { "and": [<predicate>, <predicate>, …] }
 * <or>                := { "or": [<predicate>, <predicate>, …] }
 *
 *
 * <json_matcher>      := { <selector>, "value": { <value_matcher> }} | { "value": {<value_matcher>}}
 * <selector>          := <scope>, "key": string | "key": string | <scope>
 * <scope>             := "scope": string | "scope": [string, string, …]
 *
 *
 * <value_matcher>     := <numeric_matcher> | <equals_matcher> | <presence_matcher> | <version_matcher> | <array_matcher>
 * <array_matcher>     := "array_contains": <predicate> | "array_contains": <predicate>, "index": number
 * <numeric_matcher>   := "at_least": number | "at_most": number | "at_least": number, "at_most": number
 * <equals_matcher>    := "equals": number | string | boolean | object | array
 * <presence_matcher>  := "is_present": boolean
 * <version_matcher>   := "version_matches": version matcher
 */

public class JsonPredicate private constructor(
    private val type: PredicateType,
    private val items: List<Predicate<JsonSerializable>>
) : JsonSerializable, Predicate<JsonSerializable> {

    public enum class PredicateType(internal val rawValue: String) {
        OR("or"),
        AND("and"),
        NOT("not")
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        type.rawValue to items
    ).toJsonValue()

    override fun apply(value: JsonSerializable): Boolean {
        if (items.isEmpty()) {
            return true
        }

        return when(type) {
            PredicateType.NOT -> !items.first().apply(value)
            PredicateType.OR -> items.any { it.apply(value) }
            PredicateType.AND -> items.all { it.apply(value) }

        }
    }

    /**
     * Builder class.
     */
    public class Builder public constructor() {

        public var type: PredicateType = PredicateType.OR
        internal val items: MutableList<Predicate<JsonSerializable>> = mutableListOf()

        /**
         * Sets the predicate type. If type [PredicateType.NOT], only one matcher or predicate is
         * allowed to be added.
         *
         * @param type The predicate type.
         * @return The builder instance.
         */
        public fun setPredicateType(type: PredicateType): Builder {
            return this.also { it.type = type }
        }

        /**
         * Adds a [JsonMatcher].
         *
         * @param matcher The [JsonMatcher] instance.
         * @return The builder instance.
         */
        public fun addMatcher(matcher: JsonMatcher): Builder {
            return this.also { it.items.add(matcher) }
        }

        /**
         * Adds a [JsonPredicate].
         *
         * @param predicate The [JsonPredicate] instance.
         * @return The builder instance.
         */
        public fun addPredicate(predicate: JsonPredicate): Builder {
            return this.also { it.items.add(predicate) }
        }

        /**
         * Builds the [JsonPredicate] instance.
         *
         * @return The [JsonPredicate] instance.
         * @throws IllegalArgumentException if a NOT predicate has more than one matcher or predicate
         * defined, or if the predicate does not contain at least 1 child predicate or matcher.
         */
        @kotlin.jvm.Throws(IllegalArgumentException::class)
        public fun build(): JsonPredicate {
            require(!(type == PredicateType.NOT && items.size > 1)) { "`NOT` predicate type only supports a single matcher or predicate." }

            require(items.isNotEmpty()) { "Predicate must contain at least 1 matcher or child predicate." }

            return JsonPredicate(
                type = this.type,
                items = this.items
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

        val that = other as JsonPredicate

        if (items != that.items) { return false }
        if (type != that.type) { return false }

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(items, type)
    }

    public companion object {
        /**
         * Builder factory method.
         *
         * @return A new builder instance.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }

        /**
         * Parses a [JsonValue] object into a [JsonPredicate].
         *
         * @param jsonValue The predicate as a [JsonValue].
         * @return The parsed [JsonPredicate].
         * @throws JsonException If the jsonValue defines invalid [JsonPredicate].
         */
        @JvmStatic
        @Throws(JsonException::class)
        public fun parse(jsonValue: JsonValue?): JsonPredicate {
            if (jsonValue == null || !jsonValue.isJsonMap || jsonValue.optMap().isEmpty) {
                throw JsonException("Unable to parse empty JsonValue: $jsonValue")
            }

            val map = jsonValue.optMap()

            val builder = newBuilder()

            val type = getPredicateType(map)
            if (type != null) {
                builder.setPredicateType(type)

                val subpredicatesList = map.opt(type.rawValue)
                var subpredicates = subpredicatesList.optList()

                if (PredicateType.NOT == type && subpredicatesList.isJsonMap) {
                    subpredicates = JsonList(listOf(subpredicatesList.optMap().toJsonValue()))
                }

                subpredicates
                    .filter { it.isJsonMap }
                    .forEach { child ->
                        // If the child contains a predicate type then its predicate
                        if (getPredicateType(child.optMap()) != null) {
                            builder.addPredicate(parse(child))
                            return@forEach
                        }

                        // Otherwise its a matcher
                        builder.addMatcher(JsonMatcher.parse(child))
                    }
            } else {
                builder.addMatcher(JsonMatcher.parse(jsonValue))
            }

            try {
                return builder.build()
            } catch (e: IllegalArgumentException) {
                throw JsonException("Unable to parse JsonPredicate.", e)
            }
        }

        private fun getPredicateType(jsonMap: JsonMap): PredicateType? {
            return PredicateType.entries.firstOrNull { jsonMap.containsKey(it.rawValue) }
        }
    }
}
