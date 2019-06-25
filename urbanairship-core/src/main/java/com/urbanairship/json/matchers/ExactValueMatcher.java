/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

import java.util.Map;

/**
 * Exact value matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExactValueMatcher extends ValueMatcher {

    @NonNull
    public static final String EQUALS_VALUE_KEY = "equals";

    private final JsonValue expected;

    /**
     * Default constructor.
     *
     * @param expected The expected value.
     */
    public ExactValueMatcher(@NonNull JsonValue expected) {
        this.expected = expected;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(EQUALS_VALUE_KEY, expected)
                      .build()
                      .toJsonValue();
    }

    @Override
    protected boolean apply(@NonNull JsonValue value, boolean ignoreCase) {
        return isEquals(expected, value, ignoreCase);
    }

    public boolean isEquals(@Nullable JsonValue valueOne, @Nullable JsonValue valueTwo, boolean ignoreCase) {
        valueOne = valueOne == null ? JsonValue.NULL : valueOne;
        valueTwo = valueTwo == null ? JsonValue.NULL : valueTwo;

        if (!ignoreCase) {
            return valueOne.equals(valueTwo);
        }

        if (valueOne.isString()) {
            if (!valueTwo.isString()) {
                return false;
            }

            return valueOne.optString().equalsIgnoreCase(valueTwo.getString());
        }

        if (valueOne.isJsonList()) {
            if (!valueTwo.isJsonList()) {
                return false;
            }

            JsonList listOne = valueOne.optList();
            JsonList listTwo = valueTwo.optList();

            if (listOne.size() != listTwo.size()) {
                return false;
            }

            // iterate over both lists
            for (int i = 0; i < listOne.size(); i++) {
                if (!isEquals(listOne.get(i), listTwo.get(i), ignoreCase)) {
                    return false;
                }
            }

            return true;
        }

        if (valueOne.isJsonMap()) {
            if (!valueTwo.isJsonMap()) {
                return false;
            }

            JsonMap mapOne = valueOne.optMap();
            JsonMap mapTwo = valueTwo.optMap();

            if (mapOne.size() != mapTwo.size()) {
                return false;
            }

            // iterate over both maps
            for (Map.Entry<String, JsonValue> entry : mapOne) {
                if (!mapTwo.containsKey(entry.getKey())) {
                    return false;
                }

                if (!isEquals(mapTwo.get(entry.getKey()), entry.getValue(), ignoreCase)) {
                    return false;
                }
            }

            return true;
        }

        return valueOne.equals(valueTwo);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExactValueMatcher that = (ExactValueMatcher) o;

        return expected.equals(that.expected);
    }

    @Override
    public int hashCode() {
        return expected.hashCode();
    }

}
