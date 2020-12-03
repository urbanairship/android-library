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

    /**
     * Called when the schedule needs to be prepared. The schedule might be the deferred type.
     *
     * @param schedule The schedule.
     */
    void onNewSchedule(@NonNull Schedule<? extends ScheduleData> schedule);

    /**
     * Called when the schedule is finished. The schedule might be the deferred type.
     *
     * @param schedule The schedule.
     */
    void onScheduleFinished(@NonNull Schedule<? extends ScheduleData> schedule);

    /**
     * Called when the schedule needs to be prepared.
     *
     * @param schedule The schedule.
     * @param resolvedData The schedule data. Will be different than the data on the `schedule.data` if the schedule was deferred.
     * @param callback The callback.
     */
    void onPrepareSchedule(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull T resolvedData, @NonNull AutomationDriver.PrepareScheduleCallback callback);

    /**
     * Called if the prepared schedule is no longer valid and the execution will be aborted.
     *
     * @param schedule The schedule.
     */
    @MainThread
    void onExecutionInvalidated(@NonNull Schedule<? extends ScheduleData> schedule);

    /**
     * Called when the execution was interrupted because the application terminated. Called during
     * the next app init.
     *
     * @param schedule The schedule.
     */
    @MainThread
    void onExecutionInterrupted(@NonNull Schedule<? extends ScheduleData> schedule);

    /**
     * Called when the schedule should execute.
     *
     * @param schedule The schedule.
     * @param callback The callback
     */
    @MainThread
    void onExecute(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull AutomationDriver.ExecutionCallback callback);

    /**
     * Called to check if the schedule is ready to execute.
     *
     * @param schedule The schedule.
     * @return The ready result.
     */
    @MainThread
    @AutomationDriver.ReadyResult
    int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule);

}
