package com.urbanairship.iam;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.ParseScheduleException;
import com.urbanairship.automation.ScheduleInfo;
import com.urbanairship.json.JsonException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Automation driver for in-app messaging.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class InAppMessageDriver implements AutomationDriver<InAppMessageSchedule> {

    /**
     * Fetch retry delay.
     */
    private static final long FETCH_RETRY_DELAY_MS = 30000;

    /**
     * Driver callback.
     */
    interface Callbacks {

        /**
         * Called to check if a schedule's message is ready to display.
         *
         * @param schedule The in-app message schedule.
         * @return {@code true} if the schedule's message should be displayed, otherwise {@code false}.
         */
        boolean isScheduleReadyToDisplay(@NonNull InAppMessageSchedule schedule);

        /**
         * Called to display a schedule.
         *
         * @param schedule The in-app message's schedule.
         * @param adapter The in-app message's adapter.
         * @param assets The in-app message's prefetched assets.
         */
        void onDisplay(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessageAdapter adapter, @NonNull Bundle assets);

        /**
         * Called when a schedule's message finished prefetching its assets.
         *
         * @param schedule The in-app message's schedule.
         */
        void onScheduleDataFetched(InAppMessageSchedule schedule);
    }

    private Callbacks callbacks;
    private final Handler mainHandler;
    private final Executor executor;
    private Map<String, ScheduleData> scheduleDataMap = new HashMap<>();
    private final HashMap<String, InAppMessageAdapter> adapters = new HashMap<>();

    /**
     * Default constructor.
     */
    InAppMessageDriver() {
        this(Executors.newSingleThreadExecutor());
    }

    @VisibleForTesting
    InAppMessageDriver(Executor executor) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = executor;
    }

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
        if (!scheduleDataMap.containsKey(schedule.getId())) {
            InAppMessageAdapter adapter = adapters.get(schedule.getInfo().getInAppMessage().getType());
            if (adapter != null) {
                final ScheduleData data = new ScheduleData(schedule, adapter);
                scheduleDataMap.put(schedule.getId(), data);
                prepareSchedule(data);
            } else {
                Logger.error("Missing valid InAppMessageAdapter for message: " + schedule.getInfo().getInAppMessage());
            }

            return false;
        }

        if (callbacks == null) {
            return false;
        }

        return scheduleDataMap.get(schedule.getId()).assets != null && callbacks.isScheduleReadyToDisplay(schedule);
    }

    @Override
    @MainThread
    public void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull Callback callback) {
        ScheduleData data = scheduleDataMap.get(schedule.getId());
        data.callback = callback;

        if (callbacks != null) {
            callbacks.onDisplay(schedule, data.adapter, data.assets);
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
        } catch (JsonException e) {
            throw new ParseScheduleException("Unable to parse in-app message", e);
        }

    }


    /**
     * Sets an adapter for a given display type.
     *
     * @param displayType The in-app message display type.
     * @param adapter The in-app message adapter.
     */
    void setAdapter(@InAppMessage.DisplayType String displayType, InAppMessageAdapter adapter) {
        adapters.put(displayType, adapter);
    }

    /**
     * Called to finish the display of an in-app message.
     *
     * @param scheduleId The schedule ID.
     */
    void displayFinished(String scheduleId) {
        ScheduleData data = scheduleDataMap.remove(scheduleId);
        if (data != null) {
            data.callback.onFinish();
        }
    }

    /**
     * Prepares a schedule to be displayed.
     *
     * @param scheduleData The schedule data.
     */
    private void prepareSchedule(final ScheduleData scheduleData) {
        PrepareScheduleCommand prepareScheduleCommand = new PrepareScheduleCommand(scheduleData);
        prepareScheduleCommand.addResultCallback(Looper.getMainLooper(), new ResultCallback<Integer>() {
            @Override
            public void onResult(@Nullable Integer result) {
                if (result != null && result == InAppMessageAdapter.OK) {
                    if (callbacks != null) {
                        callbacks.onScheduleDataFetched(scheduleData.schedule);
                    }
                } else {
                    // Retry after a delay
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            prepareSchedule(scheduleData);
                        }
                    }, FETCH_RETRY_DELAY_MS);
                }
            }
        });

        executor.execute(prepareScheduleCommand);
    }

    /**
     * Helper class that keeps track of the schedule's adapter, assets, and execution callback.
     */
    private static class ScheduleData {
        final InAppMessageAdapter adapter;
        final InAppMessageSchedule schedule;

        AutomationDriver.Callback callback;
        Bundle assets;

        ScheduleData(InAppMessageSchedule schedule, InAppMessageAdapter adapter) {
            this.schedule = schedule;
            this.adapter = adapter;
        }
    }

    /**
     * Runnable command to prepare a schedules assets before executing.
     */
    private static class PrepareScheduleCommand extends PendingResult<Integer> implements Runnable {

        private final ScheduleData scheduleData;

        /**
         * Default constructor.
         *
         * @param scheduleData The schedule data.
         */
        PrepareScheduleCommand(@NonNull ScheduleData scheduleData) {
            this.scheduleData = scheduleData;
        }

        @Override
        public void run() {
            final Bundle bundle = new Bundle();
            int result = scheduleData.adapter.prefetchAssets(UAirship.getApplicationContext(), scheduleData.schedule.getInfo().getInAppMessage(), bundle);

            if (result == InAppMessageAdapter.OK) {
                scheduleData.assets = bundle;
            }

            setResult(result);
        }
    }
}
