/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

/**
 * A function interface taking two source values and returning a single value.
 *
 * @param <T> The fist source type.
 * @param <U> The second source type.
 * @param <R> The return type.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface BiFunction<T, U, R> {
    /**
     * Applies the function.
     *
     * @param t The first source value.
     * @param u The second source value.
     * @return The return value.
     */
    R apply(T t, U u);
}
