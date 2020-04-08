/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.urbanairship.json.JsonMap;

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

    @IntDef({ PREPARE_RESULT_CONTINUE, PREPARE_RESULT_CANCEL, PREPARE_RESULT_PENALIZE, PREPARE_RESULT_SKIP, PREPARE_RESULT_INVALIDATE })
    @Retention(RetentionPolicy.SOURCE)
    @interface PrepareResult {}

    @IntDef({ READY_RESULT_CONTINUE, READY_RESULT_NOT_READY, READY_RESULT_INVALIDATE })
    @Retention(RetentionPolicy.SOURCE)
    @interface ReadyResult {}

    /**
     * Indicates a successful result.
     */
    int PREPARE_RESULT_CONTINUE = 0;

    /**
     * Indicates that the schedule should be canceled.
     */
    int PREPARE_RESULT_CANCEL = 1;

    /**
     * Indicates that the schedule execution should be skipped but the schedule's execution
     * count should be incremented and to handle any execution interval set on the schedule.
     */
    int PREPARE_RESULT_PENALIZE = 2;

    /**
     * Indicates that the schedule execution should be skipped.
     */
    int PREPARE_RESULT_SKIP = 3;

    /**
     * Indicates that the schedule is out of date and should be reloaded before being prepared again.
     */
    int PREPARE_RESULT_INVALIDATE = 4;

    /**
     * Schedule is ready for execution.
     */
    int READY_RESULT_CONTINUE = 1;

    /**
     * Schedule is not ready for execution.
     */
    int READY_RESULT_NOT_READY = 0;

    /**
     * Schedule is out of date and should be prepared again before it is able to be ready for execution.
     */
    int READY_RESULT_INVALIDATE = -1;

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
    void onPrepareSchedule(@NonNull T schedule, @NonNull PrepareScheduleCallback callback);

    /**
     * Checks if the schedule is ready to execute. Will be called before executing the schedule
     * but after the schedule's delay conditions are met.
     *
     * @param schedule The schedule.
     * @return The ready result.
     */
    @MainThread
    @ReadyResult
    int onCheckExecutionReadiness(@NonNull T schedule);

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
     * @param metadata The schedule's metadata.
     * @param info The generic schedule info.
     * @return A typed schedule.
     * @throws ParseScheduleException If the scheduleInfo failed to be parsed. The automation engine will delete
     * the schedule.
     */
    @NonNull
    T createSchedule(@NonNull String scheduleId, @NonNull JsonMap metadata, @NonNull ScheduleInfo info) throws ParseScheduleException;

}
