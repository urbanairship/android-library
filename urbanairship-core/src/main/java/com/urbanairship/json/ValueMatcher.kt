/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.annotation.RestrictTo
import com.urbanairship.Predicate
import com.urbanairship.json.matchers.ArrayContainsMatcher
import com.urbanairship.json.matchers.ArrayLengthMatcher
import com.urbanairship.json.matchers.ExactValueMatcher
import com.urbanairship.json.matchers.NumberRangeMatcher
import com.urbanairship.json.matchers.PresenceMatcher
import com.urbanairship.json.matchers.StringBeginsMatcher
import com.urbanairship.json.matchers.StringContainsMatcher
import com.urbanairship.json.matchers.StringEndsMatcher
import com.urbanairship.json.matchers.VersionMatcher
import com.urbanairship.util.IvyVersionMatcher

/**
 * Class representing the field matching type and values contained in a JsonMatcher.
 */
public abstract class ValueMatcher protected constructor() : JsonSerializable, Predicate<JsonSerializable> {

    override fun apply(value: JsonSerializable): Boolean {
        return apply(value, false)
    }

    /**
     * Applies the value matcher to a JSON value.
     *
     * @param jsonSerializable The JSON value.
     * @param ignoreCase `true` to ignore case when checking String values, `false` to check case.
     * @return `true` if the value matcher matches the JSON value, `false` if they do not match.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun apply(jsonSerializable: JsonSerializable?, ignoreCase: Boolean): Boolean {
        val value = jsonSerializable?.toJsonValue() ?: JsonValue.NULL
        return apply(value, ignoreCase)
    }

    /**
     * Matches a json value.
     *
     * @param jsonValue The json value.
     * @param ignoreCase `true` to ignore case when checking String values, `false` to check case.
     * @return `true` if the value matches, otherwise `false`.
     */
    public abstract fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean

    override fun toString(): String = toJsonValue().toString()

    public companion object {

        /**
         * Creates a new number range value matcher.
         *
         * @param min Optional minimum value as a double.
         * @param max Optional maximum value as a double.
         * @return A new [ValueMatcher] instance.
         * @throws IllegalArgumentException if min is greater than max.
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun newNumberRangeMatcher(min: Double?, max: Double?): ValueMatcher {
            require(!(min != null && max != null && max < min))

            return NumberRangeMatcher(min, max)
        }

        /**
         * Creates a new value matcher.
         *
         * @param value The value to apply as a [JsonValue].
         * @return A new [ValueMatcher] instance.
         */
        @JvmStatic
        public fun newValueMatcher(value: JsonValue): ValueMatcher {
            return ExactValueMatcher(value)
        }

        /**
         * Creates a new value matcher for when a field should be present.
         *
         * @return A new [ValueMatcher] instance.
         */
        @JvmStatic
        public fun newIsPresentMatcher(): ValueMatcher {
            return PresenceMatcher(true)
        }

        /**
         * Creates a new value matcher for when a field should be absent.
         *
         * @return A new [ValueMatcher] instance.
         */
        @JvmStatic
        public fun newIsAbsentMatcher(): ValueMatcher {
            return PresenceMatcher(false)
        }

        /**
         * Creates a new value matcher for a semantic version string
         *
         * @return A new ValueMatcher instance.
         * @throws IllegalArgumentException If the constraint is not a valid ivy version constraint.
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun newVersionMatcher(constraint: String): ValueMatcher {
            return VersionMatcher(IvyVersionMatcher.newMatcher(constraint))
        }

        /**
         * Creates a new array contains matcher for a specific value in the array.
         *
         * @param predicate The predicate to apply to the value at the specified index.
         * @param index The index of the value.
         * @return A new [ValueMatcher] instance.
         */
        @JvmStatic
        public fun newArrayContainsMatcher(predicate: JsonPredicate, index: Int): ValueMatcher {
            return ArrayContainsMatcher(predicate, index)
        }

        /**
         * Creates a new array contains matcher that will check the entire array.
         *
         * @param predicate The predicate to apply to each value of the array.
         * @return A new [ValueMatcher] instance.
         */
        @JvmStatic
        public fun newArrayContainsMatcher(predicate: JsonPredicate): ValueMatcher {
            return ArrayContainsMatcher(predicate, null)
        }

        /**
         * Creates a new array length matcher that will check the entire array.
         *
         * @param predicate The predicate to apply to each value of the array.
         * @return A new [ValueMatcher] instance.
         */
        @JvmStatic
        public fun newArrayLengthMatcher(predicate: JsonPredicate): ValueMatcher {
            return ArrayLengthMatcher(predicate)
        }

        /**
         * Parses a [JsonValue] object into a [ValueMatcher].
         *
         * @param jsonValue The predicate as a [JsonValue].
         * @throws JsonException if the predicate cannot be parsed.
         * @return The matcher as a [ValueMatcher].
         */
        @JvmStatic
        @Throws(JsonException::class, IllegalArgumentException::class)
        public fun parse(jsonValue: JsonValue?): ValueMatcher {
            val map = jsonValue?.optMap() ?: JsonMap.EMPTY_MAP

            if (map.containsKey(ExactValueMatcher.EQUALS_VALUE_KEY)) {
                return newValueMatcher(map.opt(ExactValueMatcher.EQUALS_VALUE_KEY))
            }

            if (map.containsKey(NumberRangeMatcher.MIN_VALUE_KEY)
                || map.containsKey(NumberRangeMatcher.MAX_VALUE_KEY)
            ) {
                val min = map[NumberRangeMatcher.MIN_VALUE_KEY]?.getDouble(0.0)
                val max = map[NumberRangeMatcher.MAX_VALUE_KEY]?.getDouble(0.0)
                try {
                    return newNumberRangeMatcher(min, max)
                } catch (e: Exception) {
                    throw JsonException("Invalid range matcher: $jsonValue", e)
                }
            }

            if (map.containsKey(PresenceMatcher.IS_PRESENT_VALUE_KEY)) {
                val isPresent = map.opt(PresenceMatcher.IS_PRESENT_VALUE_KEY).getBoolean(false)
                return if (isPresent) newIsPresentMatcher() else newIsAbsentMatcher()
            }

            if (map.containsKey(VersionMatcher.VERSION_KEY)) {
                try {
                    val constraint = map.opt(VersionMatcher.VERSION_KEY).optString()
                    return newVersionMatcher(constraint)
                } catch (e: Exception) {
                    throw JsonException(
                        "Invalid version constraint: " + map.opt(VersionMatcher.VERSION_KEY), e
                    )
                }
            }

            if (map.containsKey(VersionMatcher.ALTERNATE_VERSION_KEY)) {
                try {
                    val constraint = map.opt(VersionMatcher.ALTERNATE_VERSION_KEY).optString()
                    return newVersionMatcher(constraint)
                } catch (e: Exception) {
                    throw JsonException(
                        "Invalid version constraint: " + map.opt(VersionMatcher.ALTERNATE_VERSION_KEY),
                        e
                    )
                }
            }

            if (map.containsKey(ArrayLengthMatcher.ARRAY_LENGTH_KEY)) {
                val predicate = JsonPredicate.parse(map[ArrayLengthMatcher.ARRAY_LENGTH_KEY])
                return newArrayLengthMatcher(predicate)
            }

            if (map.containsKey(ArrayContainsMatcher.ARRAY_CONTAINS_KEY)) {
                val predicate = JsonPredicate.parse(map[ArrayContainsMatcher.ARRAY_CONTAINS_KEY])
                if (map.containsKey(ArrayContainsMatcher.INDEX_KEY)) {
                    val index = map[ArrayContainsMatcher.INDEX_KEY]?.getInt(-1)
                        ?: throw JsonException("Invalid index for array_contains matcher: " + map[ArrayContainsMatcher.INDEX_KEY])
                    return newArrayContainsMatcher(predicate, index)
                } else {
                    return newArrayContainsMatcher(predicate)
                }
            }

            if (map.containsKey(StringBeginsMatcher.STRING_BEGINS)) {
                return StringBeginsMatcher(map.opt(StringBeginsMatcher.STRING_BEGINS))
            }

            if (map.containsKey(StringEndsMatcher.STRING_ENDS)) {
                return StringEndsMatcher(map.opt(StringEndsMatcher.STRING_ENDS))
            }

            if (map.containsKey(StringContainsMatcher.STRING_CONTAINS)) {
                return StringContainsMatcher(map.opt(StringContainsMatcher.STRING_CONTAINS))
            }

            throw JsonException("Unknown value matcher: $jsonValue")
        }
    }
}
