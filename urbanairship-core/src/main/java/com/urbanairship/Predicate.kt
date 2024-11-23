/* Copyright Airship and Contributors */
package com.urbanairship

/**
 * Predicate interface.
 *
 * @param T The type of the predicate value.
 */
public fun interface Predicate<T: Any> {

    /**
     * Applies the predicate against a value.
     *
     * @param value The value to test this predicate against.
     * @return `true` if the predicate matches the object, otherwise `false`.
     */
    public fun apply(value: T): Boolean
}
