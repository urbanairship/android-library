/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Predicate;
import com.urbanairship.util.IvyVersionMatcher;

/**
 * Class representing the field matching type and values contained in a JsonMatcher.
 */
public class ValueMatcher implements JsonSerializable, Predicate<JsonSerializable> {

    private static final String MIN_VALUE_KEY = "at_least";
    private static final String MAX_VALUE_KEY = "at_most";
    private static final String EQUALS_VALUE_KEY = "equals";
    private static final String IS_PRESENT_VALUE_KEY = "is_present";
    private static final String VERSION_KEY = "version";

    private JsonValue equals;
    private Double min;
    private Double max;
    private Boolean isPresent;
    private IvyVersionMatcher versionMatcher;

    private ValueMatcher(JsonValue equals) {
        this.equals = equals;
    }

    private ValueMatcher(Double min, Double max) {
        this.min = min;
        this.max = max;
    }

    private ValueMatcher(Boolean isPresent) {
        this.isPresent = isPresent;
    }

    private ValueMatcher(IvyVersionMatcher versionMatcher) {
        this.versionMatcher = versionMatcher;
    }

    private ValueMatcher() {}

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

        return new ValueMatcher(min, max);
    }

    /**
     * Creates a new value matcher.
     *
     * @param value The value to match as a JsonValue.
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newValueMatcher(@NonNull JsonValue value) {
        return new ValueMatcher(value);
    }

    /**
     * Creates a new value matcher for when a field should be present.
     *
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newIsPresentMatcher() {
        return new ValueMatcher(true);
    }

    /**
     * Creates a new value matcher for when a field should be absent.
     *
     * @return A new ValueMatcher instance.
     */
    public static ValueMatcher newIsAbsentMatcher() {
        return new ValueMatcher(false);
    }

    /**
     * Creates a new value matcher for when a field should be absent.
     *
     * @return A new ValueMatcher instance.
     * @throws IllegalArgumentException If the constraint is not a valid ivy version constraint.
     */
    public static ValueMatcher newVersionMatcher(String constraint) {
        return new ValueMatcher(IvyVersionMatcher.newMatcher(constraint));
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(EQUALS_VALUE_KEY, equals)
                      .putOpt(MIN_VALUE_KEY, min)
                      .putOpt(MAX_VALUE_KEY, max)
                      .putOpt(IS_PRESENT_VALUE_KEY, isPresent)
                      .putOpt(VERSION_KEY, versionMatcher)
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses a JsonValue object into a ValueMatcher.
     *
     * @param jsonValue The predicate as a JsonValue.
     * @return The matcher as a ValueMatcher.
     */
    public static ValueMatcher parse(JsonValue jsonValue) throws JsonException {
        JsonMap map = jsonValue == null ? JsonMap.EMPTY_MAP : jsonValue.optMap();

        if (map.containsKey(EQUALS_VALUE_KEY)) {
            return new ValueMatcher(map.get(EQUALS_VALUE_KEY));
        }

        if (map.containsKey(MIN_VALUE_KEY) || map.containsKey(MAX_VALUE_KEY)) {
            Double min = map.containsKey(MIN_VALUE_KEY) ? map.get(MIN_VALUE_KEY).getDouble(0) : null;
            Double max = map.containsKey(MAX_VALUE_KEY) ? map.get(MAX_VALUE_KEY).getDouble(0) : null;
            return new ValueMatcher(min, max);
        }

        if (map.containsKey(IS_PRESENT_VALUE_KEY)) {
            return new ValueMatcher(map.opt(IS_PRESENT_VALUE_KEY).getBoolean(false));
        }

        if (map.containsKey(VERSION_KEY)) {
            try {
                String constraint = map.opt(VERSION_KEY).getString();
                return new ValueMatcher(IvyVersionMatcher.newMatcher(constraint));
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid version constraint: " + map.opt(VERSION_KEY), e);
            }
        }

        throw new JsonException("Unknown value matcher: " + jsonValue);
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

        if (versionMatcher != null && !(value.isString() && versionMatcher.apply(value.getString()))) {
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
