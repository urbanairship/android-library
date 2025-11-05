/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

/**
 * Functional interface representing a simple factory with a single no-arg `create()` method.
 * @param <T>
</T> */
public fun interface Factory<T> {

    /**
     * Creates a new instance.
     */
    public fun create(): T
}
