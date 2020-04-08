/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;

/**
 * Exception thrown when a scheduleInfo fails during {@link AutomationDriver#createSchedule(String, JsonMap, ScheduleInfo)}
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ParseScheduleException extends Exception {

    /**
     * Default constructor.
     *
     * @param message The exception message.
     * @param e The root cause.
     */
    public ParseScheduleException(@NonNull String message, @NonNull Throwable e) {
        super(message, e);
    }

}
