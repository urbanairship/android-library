/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Predicate;
import com.urbanairship.json.matchers.ExactValueMatcher;
import com.urbanairship.json.matchers.NumberRangeMatcher;
import com.urbanairship.json.matchers.PresenceMatcher;
import com.urbanairship.json.matchers.VersionMatcher;
import com.urbanairship.util.IvyVersionMatcher;

/**
 * Class representing the field matching type and values contained in a JsonMatcher.
 */
public abstract class ValueMatcher implements JsonSerializable, Predicate<JsonSerializable> {

    protected ValueMatcher() {}

    /**
     * Creates a new number range value matcher.
     *
     * @param min Optional minimum value as a double.
     * @param max Optional maximum value as a double.
     * @return A new ValueMatcher instance.
     * @throws IllegalArgumentException if min is greater than max.
     */
    public static ValueMatcher newNumberRangeMatcher(@Nullable Double min, @Nullable Double max) {
        if (min != null && max != null && max < min) {
            throw new IllegalArgumentException();
        }

        return new NumberRangeMatcher(min, max);
    }

    /**
     * Creates a new value matcher.
     *
     * @param value The value to match as a JsonValue.
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newValueMatcher(@NonNull JsonValue value) {
        return new ExactValueMatcher(value);
    }

    /**
     * Creates a new value matcher for when a field should be present.
     *
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newIsPresentMatcher() {
        return new PresenceMatcher(true);
    }

    /**
     * Creates a new value matcher for when a field should be absent.
     *
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newIsAbsentMatcher() {
        return new PresenceMatcher(false);
    }

    /**
     * Creates a new value matcher for when a field should be absent.
     *
     * @return A new ValueMatcher instance.
     * @throws IllegalArgumentException If the constraint is not a valid ivy version constraint.
     */
    public static ValueMatcher newVersionMatcher(String constraint) {
        return new VersionMatcher(IvyVersionMatcher.newMatcher(constraint));
    }
    /**
     * Parses a JsonValue object into a ValueMatcher.
     *
     * @param jsonValue The predicate as a JsonValue.
     * @return The matcher as a ValueMatcher.
     */
    public static ValueMatcher parse(JsonValue jsonValue) throws JsonException {
        JsonMap map = jsonValue == null ? JsonMap.EMPTY_MAP : jsonValue.optMap();

        if (map.containsKey(ExactValueMatcher.EQUALS_VALUE_KEY)) {
            return newValueMatcher(map.get(ExactValueMatcher.EQUALS_VALUE_KEY));
        }

        if (map.containsKey(NumberRangeMatcher.MIN_VALUE_KEY) || map.containsKey(NumberRangeMatcher.MAX_VALUE_KEY)) {
            Double min = map.containsKey(NumberRangeMatcher.MIN_VALUE_KEY) ? map.get(NumberRangeMatcher.MIN_VALUE_KEY).getDouble(0) : null;
            Double max = map.containsKey(NumberRangeMatcher.MAX_VALUE_KEY) ? map.get(NumberRangeMatcher.MAX_VALUE_KEY).getDouble(0) : null;
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
                String constraint = map.opt(VersionMatcher.VERSION_KEY).getString();
                return newVersionMatcher(constraint);
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid version constraint: " + map.opt(VersionMatcher.VERSION_KEY), e);
            }
        }

        throw new JsonException("Unknown value matcher: " + jsonValue);
    }


    @Override
    public String toString() {
        return toJsonValue().toString();
    }
}
