/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json.matchers;

import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

/**
 * Exact value matcher.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExactValueMatcher implements JsonValueMatcher {
    public static final String EQUALS_VALUE_KEY = "equals";

    private final JsonValue value;

    public ExactValueMatcher(JsonValue jsonValue) {
        this.value = jsonValue == null ? JsonValue.NULL : jsonValue;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(EQUALS_VALUE_KEY, value)
                      .build()
                      .toJsonValue();
    }

    @Override
    public boolean apply(JsonValue object) {
        return value.equals(object);
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

        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
