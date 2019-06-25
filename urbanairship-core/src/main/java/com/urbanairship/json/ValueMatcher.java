/* Copyright Airship and Contributors */

package com.urbanairship.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.Predicate;
import com.urbanairship.json.matchers.ArrayContainsMatcher;
import com.urbanairship.json.matchers.ExactValueMatcher;
import com.urbanairship.json.matchers.NumberRangeMatcher;
import com.urbanairship.json.matchers.PresenceMatcher;
import com.urbanairship.json.matchers.VersionMatcher;
import com.urbanairship.util.IvyVersionMatcher;

/**
 * Class representing the field matching type and values contained in a JsonMatcher.
 */
public abstract class ValueMatcher implements JsonSerializable, Predicate<JsonSerializable> {

    protected ValueMatcher() {
    }

    /**
     * Creates a new number range value matcher.
     *
     * @param min Optional minimum value as a double.
     * @param max Optional maximum value as a double.
     * @return A new ValueMatcher instance.
     * @throws IllegalArgumentException if min is greater than max.
     */
    @NonNull
    public static ValueMatcher newNumberRangeMatcher(@Nullable Double min, @Nullable Double max) {
        if (min != null && max != null && max < min) {
            throw new IllegalArgumentException();
        }

        return new NumberRangeMatcher(min, max);
    }

    /**
     * Creates a new value matcher.
     *
     * @param value The value to apply as a JsonValue.
     * @return A new ValueMatcher instance.
     */
    @NonNull
    public static ValueMatcher newValueMatcher(@NonNull JsonValue value) {
        return new ExactValueMatcher(value);
    }

    /**
     * Creates a new value matcher for when a field should be present.
     *
     * @return A new ValueMatcher instance.
     */
    @NonNull
    public static ValueMatcher newIsPresentMatcher() {
        return new PresenceMatcher(true);
    }

    /**
     * Creates a new value matcher for when a field should be absent.
     *
     * @return A new ValueMatcher instance.
     */
    @NonNull
    public static ValueMatcher newIsAbsentMatcher() {
        return new PresenceMatcher(false);
    }

    /**
     * Creates a new value matcher for a semantic version string
     *
     * @return A new ValueMatcher instance.
     * @throws IllegalArgumentException If the constraint is not a valid ivy version constraint.
     */
    @NonNull
    public static ValueMatcher newVersionMatcher(@NonNull String constraint) {
        return new VersionMatcher(IvyVersionMatcher.newMatcher(constraint));
    }

    /**
     * Creates a new array contains matcher for a specific value in the array.
     *
     * @param predicate The predicate to apply to the value at the specified index.
     * @param index The index of the value.
     * @return A new ValueMatcher instance.
     */
    @NonNull
    public static ValueMatcher newArrayContainsMatcher(@NonNull JsonPredicate predicate, int index) {
        return new ArrayContainsMatcher(predicate, index);
    }

    /**
     * Creates a new array contains matcher that will check the entire array.
     *
     * @param predicate The predicate to apply to each value of the array.
     * @return A new ValueMatcher instance.
     */
    @NonNull
    public static ValueMatcher newArrayContainsMatcher(@NonNull JsonPredicate predicate) {
        return new ArrayContainsMatcher(predicate, null);
    }

    /**
     * Parses a JsonValue object into a ValueMatcher.
     *
     * @param jsonValue The predicate as a JsonValue.
     * @return The matcher as a ValueMatcher.
     */
    @NonNull
    public static ValueMatcher parse(@Nullable JsonValue jsonValue) throws JsonException {
        JsonMap map = jsonValue == null ? JsonMap.EMPTY_MAP : jsonValue.optMap();

        if (map.containsKey(ExactValueMatcher.EQUALS_VALUE_KEY)) {
            return newValueMatcher(map.opt(ExactValueMatcher.EQUALS_VALUE_KEY));
        }

        if (map.containsKey(NumberRangeMatcher.MIN_VALUE_KEY) || map.containsKey(NumberRangeMatcher.MAX_VALUE_KEY)) {
            Double min = map.containsKey(NumberRangeMatcher.MIN_VALUE_KEY) ? map.opt(NumberRangeMatcher.MIN_VALUE_KEY).getDouble(0) : null;
            Double max = map.containsKey(NumberRangeMatcher.MAX_VALUE_KEY) ? map.opt(NumberRangeMatcher.MAX_VALUE_KEY).getDouble(0) : null;
            try {
                return newNumberRangeMatcher(min, max);
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid range matcher: " + jsonValue, e);
            }
        }

        if (map.containsKey(PresenceMatcher.IS_PRESENT_VALUE_KEY)) {
            boolean isPresent = map.opt(PresenceMatcher.IS_PRESENT_VALUE_KEY).getBoolean(false);
            return isPresent ? newIsPresentMatcher() : newIsAbsentMatcher();
        }

        if (map.containsKey(VersionMatcher.VERSION_KEY)) {
            try {
                String constraint = map.opt(VersionMatcher.VERSION_KEY).optString();
                return newVersionMatcher(constraint);
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid version constraint: " + map.opt(VersionMatcher.VERSION_KEY), e);
            }
        }

        if (map.containsKey(VersionMatcher.ALTERNATE_VERSION_KEY)) {
            try {
                String constraint = map.opt(VersionMatcher.ALTERNATE_VERSION_KEY).optString();
                return newVersionMatcher(constraint);
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid version constraint: " + map.opt(VersionMatcher.ALTERNATE_VERSION_KEY), e);
            }
        }

        if (map.containsKey(ArrayContainsMatcher.ARRAY_CONTAINS_KEY)) {
            JsonPredicate predicate = JsonPredicate.parse(map.get(ArrayContainsMatcher.ARRAY_CONTAINS_KEY));
            if (map.containsKey(ArrayContainsMatcher.INDEX_KEY)) {
                int index = map.opt(ArrayContainsMatcher.INDEX_KEY).getInt(-1);
                if (index == -1) {
                    throw new JsonException("Invalid index for array_contains matcher: " + map.get(ArrayContainsMatcher.INDEX_KEY));
                }
                return newArrayContainsMatcher(predicate, index);
            } else {
                return newArrayContainsMatcher(predicate);
            }
        }

        throw new JsonException("Unknown value matcher: " + jsonValue);
    }

    @Override
    public boolean apply(@Nullable JsonSerializable jsonSerializable) {
        return apply(jsonSerializable, false);
    }

    /**
     * Applies the value matcher to a JSON value.
     *
     * @param jsonSerializable The JSON value.
     * @param ignoreCase {@code true} to ignore case when checking String values, {@code false} to check case.
     * @return {@code true} if the value matcher matches the JSON value, {@code false} if they do not match.
     * @hide
     */
    boolean apply(@Nullable JsonSerializable jsonSerializable, boolean ignoreCase) {
        JsonValue value = jsonSerializable == null ? JsonValue.NULL : jsonSerializable.toJsonValue();
        return apply(value, ignoreCase);
    }

    /**
     * Matches a json value.
     *
     * @param jsonValue The json value.
     * @param ignoreCase {@code true} to ignore case when checking String values, {@code false} to check case.
     * @return {@code true} if the value matches, otherwise {@code false}.
     */
    protected abstract boolean apply(@NonNull JsonValue jsonValue, boolean ignoreCase);

    @NonNull
    @Override
    public String toString() {
        return toJsonValue().toString();
    }

}
