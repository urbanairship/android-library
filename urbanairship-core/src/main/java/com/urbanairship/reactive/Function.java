/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * A Simple function interface, taking and returning a single value.
 *
 * @param <T> The source type.
 * @param <R> The return type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Function<T, R> {
    /**
     * Applies the function.
     *
     * @param v The source value.
     * @return The return value.
     */
    @NonNull
    R apply(@NonNull T v);
}
