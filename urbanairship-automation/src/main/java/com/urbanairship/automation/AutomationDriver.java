/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * Driver for AutomationEngine. Handles executing and converting generic ScheduleInfo into the proper
 * Schedule class.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface AutomationDriver {

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
     * @param triggerContext The trigger context.
     * @param callback The callback to continue execution.
     */
    @WorkerThread
    void onPrepareSchedule(@NonNull Schedule schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback callback);

    /**
     * Checks if the schedule is ready to execute. Will be called before executing the schedule
     * but after the schedule's delay conditions are met.
     *
     * @param schedule The schedule.
     * @return The ready result.
     */
    @MainThread
    @ReadyResult
    int onCheckExecutionReadiness(@NonNull Schedule schedule);

    /**
     * Executes a schedule. The callback should be called after the schedule's execution is complete.
     *
     * @param schedule The triggered schedule.
     * @param finishCallback The finish callback.
     */
    @MainThread
    void onExecuteTriggeredSchedule(@NonNull Schedule schedule, @NonNull ExecutionCallback finishCallback);
}
