/* Copyright Airship and Contributors */

package com.urbanairship;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Operation scheduler.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface OperationScheduler {

    /**
     * Schedules an operation to perform after a delay.
     *
     * @param delay The delay.
     * @param operation The operation.
     */
    void schedule(long delay, @NonNull CancelableOperation operation);

}
