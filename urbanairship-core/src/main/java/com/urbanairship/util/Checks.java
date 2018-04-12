package com.urbanairship.util;

import android.support.annotation.RestrictTo;

/**
 * Validation utilities.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Checks {

    /**
     * Checks if a object is not null.
     *
     * @param value The object to check.
     * @param message The exception message if the value is null.
     */
    public static void checkNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Checks an expression.
     *
     * @param expression The expression to test.
     * @param message The exception message if the expression is false.
     */
    public static void checkArgument(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
