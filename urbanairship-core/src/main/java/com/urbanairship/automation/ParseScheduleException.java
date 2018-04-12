/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.support.annotation.RestrictTo;

/**
 * Exception thrown when a scheduleInfo fails during {@link AutomationDriver#createSchedule(String, ScheduleInfo)}
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ParseScheduleException extends Exception {

    /**
     * Default constructor.
     * @param message The exception message.
     * @param e The root cause.
     */
    public ParseScheduleException(String message, Throwable e) {
        super(message, e);
    }
}
