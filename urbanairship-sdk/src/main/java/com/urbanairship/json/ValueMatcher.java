/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Predicate;

/**
 * Class representing the field matching type and values contained in a JsonMatcher.
 */
public class ValueMatcher implements JsonSerializable, Predicate<JsonSerializable> {

    private static final String MIN_VALUE_KEY = "at_least";
    private static final String MAX_VALUE_KEY = "at_most";
    private static final String EQUALS_VALUE_KEY = "equals";
    private static final String IS_PRESENT_VALUE_KEY = "is_present";

    private final JsonValue equals;
    private final Double min;
    private final Double max;
    private final Boolean isPresent;

    private ValueMatcher(JsonValue equals, Double min, Double max, Boolean isPresent) {
        this.equals = equals;
        this.min = min;
        this.max = max;
        this.isPresent = isPresent;
    }

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

        return new ValueMatcher(null, min, max, null);
    }

    /**
     * Creates a new value matcher.
     *
     * @param value The value to match as a JsonValue.
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newValueMatcher(@NonNull JsonValue value) {
        return new ValueMatcher(value, null, null, null);
    }

    /**
     * Creates a new value matcher for when a field should be present.
     *
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newIsPresentMatcher() {
        return new ValueMatcher(null, null, null, true);
    }

    /**
     * Creates a new value matcher for when a field should be absent.
     *
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newIsAbsentMatcher() {
        return new ValueMatcher(null, null, null, false);
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(EQUALS_VALUE_KEY, equals)
                      .putOpt(MIN_VALUE_KEY, min)
                      .putOpt(MAX_VALUE_KEY, max)
                      .putOpt(IS_PRESENT_VALUE_KEY, isPresent)
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses a JsonValue object into a ValueMatcher.
     *
     * @param jsonValue The predicate as a JsonValue.
     * @return The matcher as a ValueMatcher.
     */
    public static ValueMatcher parse(JsonValue jsonValue) {
        JsonMap map = jsonValue == null ? JsonMap.EMPTY_MAP : jsonValue.optMap();

        JsonValue equals = map.get(EQUALS_VALUE_KEY);
        Double min = map.containsKey(MIN_VALUE_KEY) ? map.get(MIN_VALUE_KEY).getDouble(0) : null;
        Double max = map.containsKey(MAX_VALUE_KEY) ? map.get(MAX_VALUE_KEY).getDouble(0) : null;
        Boolean isPresent = (Boolean) map.opt(IS_PRESENT_VALUE_KEY).getValue();
        return new ValueMatcher(equals, min, max, isPresent);
    }

    @Override
    public boolean apply(JsonSerializable jsonSerializable) {
        JsonValue value = jsonSerializable == null ? JsonValue.NULL : jsonSerializable.toJsonValue();
        if (value == null) {
            value = JsonValue.NULL;
        }

        if (equals != null) {
            return equals.equals(value);
        }

        if (isPresent != null) {
            return isPresent != value.isNull();
        }

        if (min != null && (!value.isNumber() || value.getNumber().doubleValue() < min)) {
            return false;
        }

        if (max != null && (!value.isNumber() || value.getNumber().doubleValue() > max)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ValueMatcher that = (ValueMatcher) o;

        if (equals != null ? !equals.equals(that.equals) : that.equals != null) {
            return false;
        }
        if (min != null ? !min.equals(that.min) : that.min != null) {
            return false;
        }
        if (max != null ? !max.equals(that.max) : that.max != null) {
            return false;
        }
        return isPresent != null ? isPresent.equals(that.isPresent) : that.isPresent == null;

    }

    @Override
    public int hashCode() {
        int result = equals != null ? equals.hashCode() : 0;
        result = 31 * result + (min != null ? min.hashCode() : 0);
        result = 31 * result + (max != null ? max.hashCode() : 0);
        result = 31 * result + (isPresent != null ? isPresent.hashCode() : 0);
        return result;
    }
}
