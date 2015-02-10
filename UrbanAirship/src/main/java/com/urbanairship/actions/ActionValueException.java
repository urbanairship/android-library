package com.urbanairship.actions;


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
    public ActionValueException(String message, Exception exception) {
        super(message, exception);
    }
}
