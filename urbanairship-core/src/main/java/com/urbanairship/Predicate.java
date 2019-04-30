/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;

/**
 * Predicate interface.
 *
 * @param <T> predicate type.
 */
public interface Predicate<T> {

    /**
     * Applies the predicate against an object.
     *
     * @param object The object.
     * @return {@code true} if the predicate matches the object, otherwise {@code false}.
     */
    @SuppressLint("UnknownNullness")
    boolean apply(T object);

}
