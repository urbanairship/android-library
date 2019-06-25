/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

/**
 * Value presence matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PresenceMatcher extends ValueMatcher {

    @NonNull
    public static final String IS_PRESENT_VALUE_KEY = "is_present";

    private final boolean isPresent;

    /**
     * Default constructor.
     *
     * @param isPresent {@code true} if the value is required, otherwise {@code false}.
     */
    public PresenceMatcher(boolean isPresent) {
        this.isPresent = isPresent;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(IS_PRESENT_VALUE_KEY, isPresent)
                      .build()
                      .toJsonValue();
    }

    @Override
    protected boolean apply(@NonNull JsonValue value, boolean ignoreCase) {
        if (isPresent) {
            return !value.isNull();
        } else {
            return value.isNull();
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PresenceMatcher that = (PresenceMatcher) o;

        return isPresent == that.isPresent;
    }

    @Override
    public int hashCode() {
        return (isPresent ? 1 : 0);
    }

}
