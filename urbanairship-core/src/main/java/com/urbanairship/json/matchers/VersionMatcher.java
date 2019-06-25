/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.util.IvyVersionMatcher;

/**
 * Version matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VersionMatcher extends ValueMatcher {

    @NonNull
    public static final String VERSION_KEY = "version_matches";

    @NonNull
    public static final String ALTERNATE_VERSION_KEY = "version";

    private final IvyVersionMatcher versionMatcher;

    /**
     * Default constructor.
     *
     * @param matcher The version matcher.
     */
    public VersionMatcher(@NonNull IvyVersionMatcher matcher) {
        this.versionMatcher = matcher;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(VERSION_KEY, versionMatcher)
                      .build()
                      .toJsonValue();
    }

    @Override
    protected boolean apply(@NonNull JsonValue value, boolean ignoreCase) {
        return value.isString() && versionMatcher.apply(value.getString());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VersionMatcher that = (VersionMatcher) o;

        return versionMatcher.equals(that.versionMatcher);
    }

    @Override
    public int hashCode() {
        return versionMatcher.hashCode();
    }

}
