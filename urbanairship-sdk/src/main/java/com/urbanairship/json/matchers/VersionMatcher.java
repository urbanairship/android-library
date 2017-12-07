/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json.matchers;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.IvyVersionMatcher;

/**
 * Version matcher.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VersionMatcher implements JsonValueMatcher {

    public static final String VERSION_KEY = "version";

    private IvyVersionMatcher versionMatcher;

    public VersionMatcher(@NonNull IvyVersionMatcher matcher) {
        this.versionMatcher = matcher;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(VERSION_KEY, versionMatcher)
                      .build()
                      .toJsonValue();
    }

    @Override
    public boolean apply(JsonValue value) {
        if (value == null || !value.isString()) {
            return false;
        }

        return versionMatcher.apply(value.getString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VersionMatcher that = (VersionMatcher) o;

        return versionMatcher != null ? versionMatcher.equals(that.versionMatcher) : that.versionMatcher == null;
    }

    @Override
    public int hashCode() {
        return versionMatcher != null ? versionMatcher.hashCode() : 0;
    }
}
