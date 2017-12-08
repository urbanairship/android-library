/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json.matchers;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

/**
 * Exact value matcher.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExactValueMatcher extends ValueMatcher {
    public static final String EQUALS_VALUE_KEY = "equals";

    private final JsonValue expected;

    /**
     * Default constructor.
     * @param expected The expected value.
     */
    public ExactValueMatcher(@NonNull JsonValue expected) {
        this.expected = expected;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(EQUALS_VALUE_KEY, expected)
                      .build()
                      .toJsonValue();
    }

    @Override
    protected boolean apply(@NonNull JsonValue value) {
        return expected.equals(value);
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

        return expected.equals(that.expected);
    }

    @Override
    public int hashCode() {
        return expected.hashCode();
    }
}
