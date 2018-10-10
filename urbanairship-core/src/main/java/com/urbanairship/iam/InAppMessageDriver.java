package com.urbanairship.iam;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
import com.urbanairship.automation.ScheduleInfo;

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

        void onPrepareMessage(@NonNull String scheduleId, @NonNull InAppMessage message);

        /**
         * Called to check if a schedule's message is ready to display.
         *
         * @param scheduleId The in-app message schedule ID.
         * @param message The in-app message.
         * @return {@code true} if the schedule's message should be displayed, otherwise {@code false}.
         */
        @MainThread
        boolean isMessageReady(@NonNull String scheduleId, @NonNull InAppMessage message);

        /**
         * Called to display a schedule.
         *
         * @param scheduleId The in-app message's schedule ID.
         */
        @MainThread
        void onDisplay(@NonNull String scheduleId);
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
            listener.onPrepareMessage(schedule.getId(), schedule.getInfo().getInAppMessage());
        }
    }

    @Override
    @MainThread
    public boolean isScheduleReadyToExecute(@NonNull final InAppMessageSchedule schedule) {
        if (listener == null) {
            return false;
        }

        return listener.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage());
    }


    @Override
    @MainThread
    public void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull ExecutionCallback callback) {
        executionCallbacks.put(schedule.getId(), callback);
        if (listener != null) {
            listener.onDisplay(schedule.getId());
        }
    }

    @NonNull
    @Override
    public InAppMessageSchedule createSchedule(@NonNull String scheduleId, @NonNull ScheduleInfo info) throws ParseScheduleException {
        try {
            InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                            .addTriggers(info.getTriggers())
                                                                            .setDelay(info.getDelay())
                                                                            .setEnd(info.getEnd())
                                                                            .setStart(info.getStart())
                                                                            .setLimit(info.getLimit())
                                                                            .setMessage(InAppMessage.fromJson(info.getData().toJsonValue()))
                                                                            .build();

            return new InAppMessageSchedule(scheduleId, scheduleInfo);
        } catch (Exception e) {
            throw new ParseScheduleException("Unable to parse in-app message for schedule: " + scheduleId + "info data: " + info.getData(), e);
        }
    }

    /**
     * Called to finish the display of an in-app message.
     *
     * @param scheduleId The schedule ID.
     */
    void displayFinished(final String scheduleId) {
        synchronized (executionCallbacks) {
            final ExecutionCallback callback = executionCallbacks.remove(scheduleId);
            if (callback != null) {
                callback.onFinish();
            }
        }
    }

    void messagePrepared(final String scheduleId, @AutomationDriver.PrepareResult int result) {
        synchronized (prepareCallbacks) {
            final PrepareScheduleCallback callback = prepareCallbacks.remove(scheduleId);
            if (callback != null) {
                callback.onFinish(result);
            }
        }
    }

}
