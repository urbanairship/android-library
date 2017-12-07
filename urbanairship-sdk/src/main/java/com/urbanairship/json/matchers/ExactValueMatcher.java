/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json.matchers;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

/**
 * Exact value matcher.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExactValueMatcher extends ValueMatcher {
    public static final String EQUALS_VALUE_KEY = "equals";

    private final JsonValue value;

    /**
     * Default constructor.
     * @param jsonValue The expected value.
     */
    public ExactValueMatcher(@NonNull JsonValue jsonValue) {
        this.value = jsonValue;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(EQUALS_VALUE_KEY, value)
                      .build()
                      .toJsonValue();
    }

    @Override
    public boolean apply(JsonSerializable jsonSerializable) {
        JsonValue value = jsonSerializable == null ? JsonValue.NULL : jsonSerializable.toJsonValue();
        if (value == null) {
            value = JsonValue.NULL;
        }
        return value.equals(jsonSerializable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExactValueMatcher that = (ExactValueMatcher) o;

        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
