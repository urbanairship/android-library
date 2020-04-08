package com.urbanairship.iam;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
import com.urbanairship.automation.ScheduleInfo;
import com.urbanairship.json.JsonMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Automation driver for in-app messaging.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class InAppMessageDriver implements AutomationDriver<InAppMessageSchedule> {

    /**
     * Driver callback.
     */
    interface Listener {

        /**
         * Called to prepare the schedule. After preparation is complete,
         * call {@link InAppMessageDriver#schedulePrepared(String, int)}.
         *
         * @param schedule The in-app message schedule.
         */
        void onPrepareSchedule(@NonNull InAppMessageSchedule schedule);

        /**
         * Called to check if a schedule is ready to execute.
         *
         * @param schedule The in-app message schedule.
         * @return The ready result.
         */
        @ReadyResult
        @MainThread
        int onCheckExecutionReadiness(@NonNull InAppMessageSchedule schedule);

        /**
         * Called to execute the schedule. After execution is complete,
         * call {@link InAppMessageDriver#scheduleExecuted(String)}.
         *
         * @param schedule The in-app message schedule.
         */
        @MainThread
        void onExecuteSchedule(@NonNull InAppMessageSchedule schedule);

    }

    private Listener listener;
    private final Map<String, AutomationDriver.ExecutionCallback> executionCallbacks = new HashMap<>();
    private final Map<String, AutomationDriver.PrepareScheduleCallback> prepareCallbacks = new HashMap<>();

    /**
     * Sets the listener for the driver.
     *
     * @param listener The driver listener.
     */
    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onPrepareSchedule(@NonNull InAppMessageSchedule schedule, @NonNull PrepareScheduleCallback callback) {
        synchronized (prepareCallbacks) {
            prepareCallbacks.put(schedule.getId(), callback);
        }

        if (listener != null) {
            listener.onPrepareSchedule(schedule);
        }
    }

    @Override
    @MainThread
    @ReadyResult
    public int onCheckExecutionReadiness(@NonNull final InAppMessageSchedule schedule) {
        if (listener == null) {
            return READY_RESULT_NOT_READY;
        }

        return listener.onCheckExecutionReadiness(schedule);
    }

    @Override
    @MainThread
    public void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull ExecutionCallback callback) {
        executionCallbacks.put(schedule.getId(), callback);
        if (listener != null) {
            listener.onExecuteSchedule(schedule);
        }
    }

    @NonNull
    @Override
    public InAppMessageSchedule createSchedule(@NonNull String scheduleId, @NonNull JsonMap metadata, @NonNull ScheduleInfo info) throws ParseScheduleException {
        try {
            InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                            .addTriggers(info.getTriggers())
                                                                            .setDelay(info.getDelay())
                                                                            .setEnd(info.getEnd())
                                                                            .setStart(info.getStart())
                                                                            .setLimit(info.getLimit())
                                                                            .setMessage(InAppMessage.fromJson(info.getData().toJsonValue()))
                                                                            .build();

            return new InAppMessageSchedule(scheduleId, metadata, scheduleInfo);
        } catch (Exception e) {
            throw new ParseScheduleException("Unable to parse in-app message for schedule: " + scheduleId + "info data: " + info.getData(), e);
        }
    }

    /**
     * Called to finish the display of an in-app message.
     *
     * @param scheduleId The schedule ID.
     */
    void scheduleExecuted(final String scheduleId) {
        synchronized (executionCallbacks) {
            final ExecutionCallback callback = executionCallbacks.remove(scheduleId);
            if (callback != null) {
                callback.onFinish();
            }
        }
    }

    void schedulePrepared(final String scheduleId, @AutomationDriver.PrepareResult int result) {
        synchronized (prepareCallbacks) {
            final PrepareScheduleCallback callback = prepareCallbacks.remove(scheduleId);
            if (callback != null) {
                callback.onFinish(result);
            }
        }
    }

}
