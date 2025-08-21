package com.urbanairship.util

import androidx.annotation.RestrictTo

/**
 * Validation utilities.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object Checks {

    /**
     * Checks if an object is not null.
     *
     * @param value The object to check.
     * @param message The exception message if the value is null.
     * @throws IllegalArgumentException if the value is null.
     */
    public fun checkNotNull(value: Any?, message: String) {
        requireNotNull(value) { message }
    }

    /**
     * Checks an expression.
     *
     * @param expression The expression to test.
     * @param message The exception message if the expression is false.
     * @throws IllegalArgumentException if the expression evaluates to false.
     */
    public fun checkArgument(expression: Boolean, message: String) {
        require(expression) { message }
    }
}
