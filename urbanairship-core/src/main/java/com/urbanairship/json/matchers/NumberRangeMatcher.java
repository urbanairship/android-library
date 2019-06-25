/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

/**
 * Range matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NumberRangeMatcher extends ValueMatcher {

    @NonNull
    public static final String MIN_VALUE_KEY = "at_least";

    @NonNull
    public static final String MAX_VALUE_KEY = "at_most";

    @Nullable
    private final Double min;

    @Nullable
    private final Double max;

    /**
     * Default constructor.
     *
     * @param min The min value.
     * @param max The max value.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public NumberRangeMatcher(@Nullable Double min, @Nullable Double max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NumberRangeMatcher that = (NumberRangeMatcher) o;

        if (min != null ? !min.equals(that.min) : that.min != null) {
            return false;
        }
        return max != null ? max.equals(that.max) : that.max == null;
    }

    @Override
    public int hashCode() {
        int result = min != null ? min.hashCode() : 0;
        result = 31 * result + (max != null ? max.hashCode() : 0);
        return result;
    }

    @Override
    protected boolean apply(@NonNull JsonValue value, boolean ignoreCase) {
        if (min != null && (!value.isNumber() || value.getDouble(0) < min)) {
            return false;
        }

        return max == null || (value.isNumber() && !(value.getDouble(0) > max));
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(MIN_VALUE_KEY, min)
                      .putOpt(MAX_VALUE_KEY, max)
                      .build()
                      .toJsonValue();
    }

}
