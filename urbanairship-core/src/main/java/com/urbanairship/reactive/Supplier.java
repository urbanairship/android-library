/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * A Simple function interface, taking no arguments and returning a single value.
 *
 * @param <T> The return type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Supplier<T> {

    /**
     * Applies the function.
     *
     * @return The return value.
     */
    @NonNull
    T apply();

}
