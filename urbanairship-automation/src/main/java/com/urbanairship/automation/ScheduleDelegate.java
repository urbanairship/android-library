/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * In-App Automation schedule delegate.
 *
 * @param <T> The schedule type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface ScheduleDelegate<T extends ScheduleData> {

    void onPrepareSchedule(@NonNull String scheduleId, @NonNull T scheduleData, @NonNull AutomationDriver.PrepareScheduleCallback callback);

    @MainThread
    void onExecutionInvalidated(@NonNull String scheduleId);

    @MainThread
    void onExecute(@NonNull String scheduleId, @NonNull AutomationDriver.ExecutionCallback callback);

    @MainThread
    @AutomationDriver.ReadyResult
    int onCheckExecutionReadiness(@NonNull String scheduleId);

}
