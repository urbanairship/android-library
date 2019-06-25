package com.urbanairship.actions;

import androidx.annotation.Nullable;

/**
 * Exceptions thrown when creating ActionValues from objects.
 */
public class ActionValueException extends Exception {

    /**
     * Default constructor.
     *
     * @param message The exception's message.
     * @param exception The cause of the exception.
     */
    public ActionValueException(@Nullable String message, @Nullable Exception exception) {
        super(message, exception);
    }

}
