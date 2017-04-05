/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

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
    boolean apply(T object);
}
