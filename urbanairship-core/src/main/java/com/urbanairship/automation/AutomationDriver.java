/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Driver for AutomationEngine. Handles executing and converting generic ScheduleInfo into the proper
 * Schedule class.
 *
 * @param <T> The schedule type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface AutomationDriver<T extends Schedule> {

    @IntDef({ RESULT_CONTINUE, RESULT_CANCEL, RESULT_PENALIZE, RESULT_SKIP })
    @Retention(RetentionPolicy.SOURCE)
    @interface PrepareResult {}

    /**
     * Indicates a successful result.
     */
    int RESULT_CONTINUE = 0;

    /**
     * Indicates that the schedule should be canceled.
     */
    int RESULT_CANCEL = 1;

    /**
     * Indicates that the schedule execution should be skipped but the schedule's execution
     * count should be incremented and to handle any execution interval set on the schedule.
     */
    int RESULT_PENALIZE = 2;

    /**
     * Indicates that the schedule execution should be skipped.
     */
    int RESULT_SKIP = 3;

    /**
     * The execution callback.
     */
    interface ExecutionCallback {

        /**
         * Call when the schedule is finished executing.
         */
        void onFinish();
    }

    /**
     * The prepare schedule callback.
     */
    interface PrepareScheduleCallback {

        /**
         * Call when the schedule is finished preparing the schedule.
         */
        void onFinish(@PrepareResult int result);
    }

    /**
     * Called on a triggered schedule before execution. This is called on a worker
     * thread but adapters should offload any long tasks onto another thread to
     * avoid blocking other schedules from executing.
     *
     * @param schedule The schedule.
     * @param callback The callback to continue execution.
     */
    @WorkerThread
    void onPrepareSchedule(T schedule, @NonNull PrepareScheduleCallback callback);

    /**
     * Checks if the schedule is ready to execute. Will be called before executing the schedule
     * but after the schedule's delay conditions are met.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule is ready, otherwise {@code false}.
     */
    @MainThread
    boolean isScheduleReadyToExecute(T schedule);


    /**
     * Executes a schedule. The callback should be called after the schedule's execution is complete.
     *
     * @param schedule The triggered schedule.
     * @param finishCallback The finish callback.
     */
    @MainThread
    void onExecuteTriggeredSchedule(@NonNull T schedule, @NonNull ExecutionCallback finishCallback);

    /**
     * Creates a typed schedule from a generic schedule info and ID.
     *
     * @param scheduleId The schedule ID.
     * @param info The generic schedule info.
     * @return A typed schedule.
     * @throws ParseScheduleException If the scheduleInfo is unparsable. The automation engine will delete
     * the schedule.
     */
    @NonNull
    T createSchedule(String scheduleId, @NonNull ScheduleInfo info) throws ParseScheduleException;
}
