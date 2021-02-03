/* Copyright Airship and Contributors */

package com.urbanairship.job;

import androidx.annotation.RestrictTo;

/**
 * Exceptions thrown by the Schedulers.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SchedulerException extends Exception {

    /**
     * Default constructor.
     *
     * @param message The exception message.
     * @param e The root exception.
     */
    SchedulerException(String message, Exception e) {
        super(message, e);
    }

    /**
     * Creates a scheduler exception for the given message.
     *
     * @param message The exception message.
     */
    SchedulerException(String message) {
        super(message);
    }

}
