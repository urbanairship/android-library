/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * A function interface taking two source values and returning a single value.
 *
 * @param <T> The fist source type.
 * @param <U> The second source type.
 * @param <R> The return type.
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
    @NonNull
    R apply(@NonNull T t, @NonNull U u);

}
