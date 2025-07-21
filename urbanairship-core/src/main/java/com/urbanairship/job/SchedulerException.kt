/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.annotation.RestrictTo

/**
 * Exceptions thrown by the Schedulers.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SchedulerException : Exception {

    /**
     * Default constructor.
     *
     * @param message The exception message.
     * @param e The root exception.
     */
    internal constructor(message: String?, e: Exception?) : super(message, e)

    /**
     * Creates a scheduler exception for the given message.
     *
     * @param message The exception message.
     */
    internal constructor(message: String?) : super(message)
}
