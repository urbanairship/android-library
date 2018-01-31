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
    interface Callbacks {

        /**
         * Called to check if a schedule's message is ready to display.
         *
         * @param scheduleId The in-app message schedule ID.
         * @param message The in-app message.
         * @return {@code true} if the schedule's message should be displayed, otherwise {@code false}.
         */
        boolean isMessageReady(@NonNull String scheduleId, @NonNull InAppMessage message);

        /**
         * Called to display a schedule.
         *
         * @param scheduleId The in-app message's schedule ID.
         */
        void onDisplay(@NonNull String scheduleId);
    }

    private Callbacks callbacks;
    private Map<String, AutomationDriver.Callback> scheduleFinishCallbacks = new HashMap<>();

    /**
     * Sets the callbacks for the driver.
     *
     * @param callbacks The driver callbacks.
     */
    void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    @MainThread
    public boolean isScheduleReadyToExecute(final InAppMessageSchedule schedule) {
        if (callbacks == null) {
            return false;
        }

        return callbacks.isMessageReady(schedule.getId(), schedule.getInfo().getInAppMessage());
    }

    @Override
    @MainThread
    public void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull Callback callback) {
        scheduleFinishCallbacks.put(schedule.getId(), callback);
        if (callbacks != null) {
            callbacks.onDisplay(schedule.getId());
        }
    }

    @NonNull
    @Override
    public InAppMessageSchedule createSchedule(String scheduleId, @NonNull ScheduleInfo info) throws ParseScheduleException {
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
        final Callback callback = scheduleFinishCallbacks.remove(scheduleId);
        if (callback != null) {
            callback.onFinish();
        }
    }

}
