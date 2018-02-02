/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

/**
 * Driver for AutomationEngine. Handles executing and converting generic ScheduleInfo into the proper
 * Schedule class.
 *
 * @param <T> The schedule type.
 */
public interface AutomationDriver<T extends Schedule> {

    /**
     * The finish callback.
     */
    interface Callback {

        /**
         * Called when the schedule is finished executing.
         */
        void onFinish();
    }

    /**
     * Checks if the schedule is ready to execute. Will be called before executing the schedule
     * but after the schedule's delay conditions are met.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule is ready, otherwise {@code false}.
     */
    boolean isScheduleReadyToExecute(T schedule);


    /**
     * Executes a schedule. The callback should be called after the schedule's execution is complete.
     *
     * @param schedule The triggered schedule.
     * @param finishCallback The finish callback.
     */
    @MainThread
    void onExecuteTriggeredSchedule(@NonNull T schedule, @NonNull Callback finishCallback);

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
