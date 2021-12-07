/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

/**
 * Functional interface representing a simple factory with a single no-arg {@code create()} method.
 * @param <T>
 */
@FunctionalInterface
public interface Factory <T> {

    /**
     * Creates a new instance.
     */
    T create();
}
