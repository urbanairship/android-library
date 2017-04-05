/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

/**
 * Thrown when a JsonValue is unable to wrap an object or unable to parse a JSON encoded String.
 */
public class JsonException extends Exception {

    /**
     * Constructs a new JsonException with the current stack trace and the
     * specified detail message.
     *
     * @param message The detail message for this exception.
     */
    public JsonException(String message) {
        super(message);
    }

    /**
     * Constructs a new JsonException with the current stack trace, the
     * specified detail message and the specified cause.
     *
     * @param message The detail message for this exception.
     * @param cause The cause of this exception.
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
