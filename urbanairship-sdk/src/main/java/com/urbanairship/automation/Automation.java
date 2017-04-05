/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AnalyticsListener;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.RegionEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * This class is the primary interface to the Urban Airship On Device Automation API. If accessed outside
 * of the main process, the class methods will no-op.
 */
public class Automation extends AirshipComponent {

    private static final String KEY_PREFIX = "com.urbanairship.automation";
    private static final String AUTOMATION_ENABLED_KEY = KEY_PREFIX + ".AUTOMATION_ENABLED";

    private final AutomationDataManager dataManager;
    private final PreferenceDataStore preferenceDataStore;
    private final ActivityMonitor.Listener listener;
    private final Analytics analytics;
    private final ActivityMonitor activityMonitor;


    private final Handler backgroundHandler;

    @VisibleForTesting
    final HandlerThread backgroundThread;

    private AnalyticsListener analyticsListener;

    private boolean automationEnabled = false;
    private List<ScheduleRunnable<Void>> delayedRunnables = new ArrayList<>();

    private static String screen;
    private static String regionId;
    private static boolean isForeground = false;

    /**
     * Automation schedules limit.
     */
    public static final long SCHEDULES_LIMIT = 1000;

    /**
     * Default constructor.==
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @param analytics The analytics instance.
     * @hide
     */
    public Automation(@NonNull Context context, @NonNull AirshipConfigOptions configOptions,
                      @NonNull Analytics analytics, @NonNull PreferenceDataStore preferenceDataStore,
                      @NonNull ActivityMonitor activityMonitor) {
        this(analytics, new AutomationDataManager(context, configOptions.getAppKey()), preferenceDataStore, activityMonitor);
    }

    Automation(@NonNull Analytics analytics, @NonNull AutomationDataManager dataManager,
               @NonNull PreferenceDataStore preferenceDataStore, @NonNull ActivityMonitor activityMonitor) {
        this.analytics = analytics;
        this.dataManager = dataManager;
        this.preferenceDataStore = preferenceDataStore;
        this.listener = new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long time) {
                isForeground = true;
                Automation.this.onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_FOREGROUND, 1.00);
                onScheduleConditionsChanged();
            }

            @Override
            public void onBackground(long time) {
                isForeground = false;
                Automation.this.onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_BACKGROUND, 1.00);
                onScheduleConditionsChanged();
            }
        };
        this.activityMonitor = activityMonitor;
        this.backgroundThread = new HandlerThread("delayed");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    protected void init() {
        if (!UAirship.isMainProcess()) {
            return;
        }

        if (analyticsListener == null) {
            analyticsListener = new AnalyticsListener() {
                @Override
                public void onRegionEventAdded(RegionEvent regionEvent) {
                    regionId = regionEvent.toJsonValue().getMap().opt("region_id").getString();
                    int type = regionEvent.getBoundaryEvent() == RegionEvent.BOUNDARY_EVENT_ENTER ? Trigger.REGION_ENTER : Trigger.REGION_EXIT;
                    onEventAdded(regionEvent.toJsonValue(), type, 1.00);

                    onScheduleConditionsChanged();
                }

                @Override
                public void onCustomEventAdded(CustomEvent customEvent) {
                    onEventAdded(customEvent.toJsonValue(), Trigger.CUSTOM_EVENT_COUNT, 1.00);

                    BigDecimal eventValue = customEvent.getEventValue();
                    if (eventValue != null) {
                        onEventAdded(customEvent.toJsonValue(), Trigger.CUSTOM_EVENT_VALUE, eventValue.doubleValue());
                    }
                }

                @Override
                public void onScreenTracked(String screenName) {
                    screen = screenName;
                    onEventAdded(JsonValue.wrap(screenName), Trigger.SCREEN_VIEW, 1.00);

                    onScheduleConditionsChanged();
                }
            };
        }

        activityMonitor.addListener(listener);
        analytics.addAnalyticsListener(analyticsListener);
        automationEnabled = preferenceDataStore.getBoolean(AUTOMATION_ENABLED_KEY, false);

        rescheduleDelays();
    }

    @Override
    protected void tearDown() {
        if (!UAirship.isMainProcess()) {
            return;
        }

        cancelAllDelays();
        activityMonitor.removeListener(listener);
        backgroundThread.quit();
    }

    /**
     * Schedules an {@link ActionScheduleInfo} instance.
     *
     * @param scheduleInfo The {@link ActionScheduleInfo} instance.
     * @return The scheduled {@link ActionSchedule} containing the relevant
     * {@link ActionScheduleInfo} and generated schedule ID. May return null
     * if the scheduling failed or the schedule count is greater than or equal
     * to {@link #SCHEDULES_LIMIT}.
     */
    @WorkerThread
    public ActionSchedule schedule(ActionScheduleInfo scheduleInfo) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return null;
        }

        if (dataManager.getScheduleCount() >= SCHEDULES_LIMIT) {
            Logger.error("AutomationDataManager - unable to insert schedule due to exceeded schedule limit.");
            return null;
        }

        List<ActionSchedule> insertSchedules = dataManager.insertSchedules(Collections.singletonList(scheduleInfo));

        if (insertSchedules.isEmpty()) {
            return null;
        }

        ActionSchedule insertedSchedule = insertSchedules.get(0);

        if (!automationEnabled) {
            automationEnabled = true;
            preferenceDataStore.put(AUTOMATION_ENABLED_KEY, true);
        }

        Logger.debug("Automation - action schedule inserted: " + insertedSchedule);

        return insertedSchedule;
    }

    /**
     * Schedules an {@link ActionScheduleInfo} instance asynchronously.
     *
     * @param scheduleInfo The {@link ActionScheduleInfo} instance.
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation. The value
     * returned to {@link com.urbanairship.PendingResult.ResultCallback#onResult(Object)} may be null
     * if the scheduling failed, the schedule count is greater than or equal to {@link #SCHEDULES_LIMIT},
     * or the scheduling was attempted off of the main process.
     */
    public void scheduleAsync(final ActionScheduleInfo scheduleInfo, @Nullable final PendingResult.ResultCallback<ActionSchedule> callback) {
        final Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();

        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation and executing callback.");
            runCallback(callback, null, looper);
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                final ActionSchedule schedule = schedule(scheduleInfo);
                runCallback(callback, schedule, looper);
            }
        });
    }

    /**
     * Schedules a list of {@link ActionScheduleInfo} instances.
     *
     * @param scheduleInfos The list of {@link ActionScheduleInfo} instances.
     * @return The list of scheduled {@link ActionSchedule} instances, each containing the relevant
     * {@link ActionScheduleInfo} and generated schedule ID. May return {@link Collections#emptyList()}
     * if the scheduling failed or the schedule count is greater than or equal
     * to {@link #SCHEDULES_LIMIT}.
     */
    @WorkerThread
    public List<ActionSchedule> schedule(List<ActionScheduleInfo> scheduleInfos) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return Collections.emptyList();
        }

        if (dataManager.getScheduleCount() + scheduleInfos.size() >= SCHEDULES_LIMIT) {
            Logger.error("AutomationDataManager - unable to insert schedule due to schedule exceeded limit.");
            return Collections.emptyList();
        }

        List<ActionSchedule> actionSchedules = dataManager.insertSchedules(scheduleInfos);
        if (!actionSchedules.isEmpty()) {
            if (!automationEnabled) {
                automationEnabled = true;
                preferenceDataStore.put(AUTOMATION_ENABLED_KEY, true);
            }

            Logger.debug("Automation - action schedule inserted: " + actionSchedules);
        }

        return actionSchedules;
    }

    /**
     * Schedules a list of {@link ActionScheduleInfo} instances asynchronously.
     *
     * @param scheduleInfos The list of {@link ActionScheduleInfo} instances.
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation. The value
     * returned to {@link com.urbanairship.PendingResult.ResultCallback#onResult(Object)} may be
     * {@link Collections#emptyList()} if the scheduling failed, the schedule count is greater than or equal
     * to {@link #SCHEDULES_LIMIT}, or the scheduling was attempted off of the main process.
     */
    public void scheduleAsync(final List<ActionScheduleInfo> scheduleInfos, final PendingResult.ResultCallback<List<ActionSchedule>> callback) {
        final Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();

        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation and executing callback.");
            runCallback(callback, Collections.<ActionSchedule>emptyList(), looper);
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                final List<ActionSchedule> schedule = schedule(scheduleInfos);
                runCallback(callback, schedule, looper);
            }
        });
    }

    /**
     * Cancels a schedule for a given schedule ID.
     *
     * @param id The schedule ID.
     */
    @WorkerThread
    public void cancel(String id) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dataManager.deleteSchedule(id);
        cancelScheduleDelays(Collections.singletonList(id));
    }

    /**
     * Cancels a schedule for a given schedule ID asynchronously.
     *
     * @param id The schedule ID.
     */
    public void cancelAsync(final String id) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cancel(id);
            }
        });

    }

    /**
     * Cancels schedules for a given list of schedule IDs.
     *
     * @param ids The list of schedule IDs.
     */
    @WorkerThread
    public void cancel(List<String> ids) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dataManager.bulkDeleteSchedules(ids);
        cancelScheduleDelays(ids);
    }

    /**
     * Cancels schedules for a given list of schedule IDs asynchronously.
     *
     * @param ids The list of schedule IDs.
     */
    public void cancelAsync(final List<String> ids) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cancel(ids);
            }
        });
    }

    /**
     * Cancels a group of schedules.
     *
     * @param group The schedule group.
     */
    @WorkerThread
    public void cancelGroup(String group) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dataManager.deleteSchedules(group);
        cancelGroupDelays(group);
    }

    /**
     * Cancels a group of schedules asynchronously.
     *
     * @param group The schedule group.
     */
    public void cancelGroupAsync(final String group) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cancelGroup(group);
            }
        });
    }

    /**
     * Cancels all schedules.
     */
    @WorkerThread
    public void cancelAll() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dataManager.deleteSchedules();
        cancelAllDelays();
    }

    /**
     * Cancels all schedules asynchronously.
     */
    public void cancelAllAsync() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cancelAll();
            }
        });
    }

    /**
     * Cancel delay schedule handler by a group.
     *
     * @param group A schedule identifier.
     */
    @WorkerThread
    private void cancelGroupDelays(String group) {
        synchronized (delayedRunnables) {
            for (ScheduleRunnable runnable : new ArrayList<>(delayedRunnables)) {
                if (group.equals(runnable.group)) {
                    backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
                    delayedRunnables.remove(runnable);
                }
            }
        }
    }

    /**
     * Cancel delayed schedules by schedule identifiers.
     *
     * @param scheduleIds A set of identifiers to cancel.
     */
    @WorkerThread
    private void cancelScheduleDelays(Collection<String> scheduleIds) {
        synchronized (delayedRunnables) {
            for (ScheduleRunnable runnable : new ArrayList<>(delayedRunnables)) {
                if (scheduleIds.contains(runnable.scheduleId)) {
                    backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
                    delayedRunnables.remove(runnable);
                }
            }
        }
    }

    private void cancelAllDelays() {
        synchronized (delayedRunnables) {
            for (ScheduleRunnable runnable : delayedRunnables) {
                backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
            }

            delayedRunnables.clear();
        }
    }

    /**
     * Reschedule timers for any schedule that is pending execution and has a future delayed
     * execution date.
     */
    @WorkerThread
    private void rescheduleDelays() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {

                List < ActionSchedule > actionSchedules = dataManager.getDelayedSchedules();
                if (actionSchedules.isEmpty()) {
                    return;
                }

                Map<Long, List<String>> toReschedule = new HashMap<>();
                Map<ActionSchedule, Long> timerList = new HashMap<>();
                List<ActionSchedule> toExecute = new ArrayList<>();

                for (ActionSchedule schedule : actionSchedules) {
                    if (schedule.getIsPendingExecution()) {
                        if ((schedule.getPendingExecutionDate() - System.currentTimeMillis()) > (schedule.getInfo().getDelay().getSeconds() * 1000)) {
                            updateListMap(toReschedule, System.currentTimeMillis() + 1000 * schedule.getInfo().getDelay().getSeconds(), schedule.getId());
                            timerList.put(schedule, null);
                        } else if (schedule.getPendingExecutionDate() > System.currentTimeMillis()) {
                            timerList.put(schedule, schedule.getPendingExecutionDate() - System.currentTimeMillis());
                        } else {
                            toExecute.add(schedule);
                        }
                    }
                }

                // Update schedule database
                Map<String, List<String>> queryMap = new HashMap<>();
                addSchedulesToSet(queryMap, toReschedule);
                if (!queryMap.isEmpty()) {
                    dataManager.updateLists(queryMap);
                }

                // Handle schedules that need to be executed
                if (!toExecute.isEmpty()) {
                    handleTriggeredSchedules(toExecute);
                }

                // Set timers
                for (Map.Entry<ActionSchedule, Long> entry : timerList.entrySet()) {
                    if (entry.getValue() != null) {
                        startDelayTimer(entry.getKey(), entry.getValue());
                    } else {
                        startDelayTimer(entry.getKey());
                    }
                }
            }
        });
    }


    /**
     * Gets a schedule for a given schedule ID.
     *
     * @param id The schedule ID.
     * @return The retrieved {@link ActionSchedule}.
     */
    @WorkerThread
    @Nullable
    public ActionSchedule getSchedule(String id) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return null;
        }

        return dataManager.getSchedule(id);
    }

    /**
     * Gets a schedule for a given schedule ID asynchronously.
     *
     * @param id The schedule ID.
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation. The value
     * returned to {@link com.urbanairship.PendingResult.ResultCallback#onResult(Object)} may be
     * null if the schedule does not exist or the get was attempted off of the main process.
     */
    public void getScheduleAsync(final String id, final PendingResult.ResultCallback<ActionSchedule> callback) {
        final Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();

        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation and executing callback.");
            runCallback(callback, null, looper);
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                final ActionSchedule schedule = getSchedule(id);
                runCallback(callback, schedule, looper);
            }
        });
    }

    /**
     * Gets all schedules.
     *
     * @return The list of retrieved {@link ActionSchedule} instances.
     */
    @WorkerThread
    public List<ActionSchedule> getSchedules() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return Collections.emptyList();
        }

        return dataManager.getSchedules();
    }


    /**
     * Get schedules for a list of IDs.
     */
    @WorkerThread
    public List<ActionSchedule> getSchedules(Set<String> scheduleIds) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return Collections.emptyList();
        }

        return dataManager.getSchedules(scheduleIds);
    }

    /**
     * Gets all schedules asynchronously.
     *
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation. The value
     * returned to {@link com.urbanairship.PendingResult.ResultCallback#onResult(Object)} may be
     * {@link Collections#emptyList()} if no schedules exist or the get was attempted off of the main process.
     */
    public void getSchedulesAsync(final PendingResult.ResultCallback<List<ActionSchedule>> callback) {
        final Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();

        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation and executing callback.");
            runCallback(callback, Collections.<ActionSchedule>emptyList(), looper);
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                final List<ActionSchedule> schedules = getSchedules();
                runCallback(callback, schedules, looper);
            }
        });
    }

    /**
     * Gets all schedules for a given group.
     *
     * @param group The group.
     * @return The list of retrieved {@link ActionSchedule} instances.
     */
    @WorkerThread
    public List<ActionSchedule> getSchedules(String group) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return Collections.emptyList();
        }

        return dataManager.getSchedules(group);
    }

    /**
     * Gets all schedules for a given group asynchronously.
     *
     * @param group The group.
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation. The value
     * returned to {@link com.urbanairship.PendingResult.ResultCallback#onResult(Object)} may be
     * {@link Collections#emptyList()} if no schedules exist or the get was attempted off of the main process.
     */
    public void getSchedulesAsync(final String group, final PendingResult.ResultCallback<List<ActionSchedule>> callback) {
        final Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();

        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation and executing callback.");
            runCallback(callback, Collections.<ActionSchedule>emptyList(), looper);
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                final List<ActionSchedule> schedules = getSchedules(group);
                runCallback(callback, schedules, looper);
            }
        });
    }

    /**
     * Called when one of the schedule conditions changes.
     */
    private void onScheduleConditionsChanged() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<ActionSchedule> schedules = dataManager.getDelayedSchedules();
                List<ActionSchedule> schedulesToCheck = new ArrayList<>();

                for (ActionSchedule actionSchedule : schedules) {
                    if (actionSchedule.getIsPendingExecution()  && (actionSchedule.getPendingExecutionDate() == -1 || actionSchedule.getPendingExecutionDate() <= System.currentTimeMillis())) {
                        schedulesToCheck.add(actionSchedule);
                    }
                }

                if (!schedulesToCheck.isEmpty()) {
                    handleTriggeredSchedules(schedulesToCheck);
                }
            }
        });
    }

    /**
     * For a given event, retrieves and iterates through any relevant triggers. If a trigger goal
     * is achieved, the correlated schedule is retrieved and the action is applied. The trigger progress
     * and schedule count will then either be incremented or reset / removed.
     *
     * @param json The relevant event data.
     * @param type The event type.
     * @param value The trigger value to increment by.
     */
    private void onEventAdded(final JsonSerializable json, final int type, final double value) {
        if (!automationEnabled) {
            return;
        }

        Logger.debug("Automation - updating triggers with type: " + type);

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<TriggerEntry> triggerEntries = dataManager.getActiveTriggers(type);
                if (triggerEntries.isEmpty()) {
                    return;
                }

                List<String> cancellationTriggersToIncrement = new ArrayList<>();
                List<String> standardTriggersToIncrement = new ArrayList<>();
                List<String> triggersToReset = new ArrayList<>();
                List<String> schedulesToResetExecutionState = new ArrayList<>();

                // Schedule ID to triggers map
                Map<String, String> standardTriggerMap = new HashMap<>();
                Map<String, String> cancellationTriggerMap = new HashMap<>();

                Set<String> triggeredSchedules = new HashSet<>();

                for (TriggerEntry trigger : triggerEntries) {
                    if ((json != null && (trigger.getPredicate() != null && !trigger.getPredicate().apply(json)))) {
                        continue;
                    }

                    double progress = trigger.getProgress() + value;

                    if (progress >= trigger.getGoal()) {
                        triggersToReset.add(trigger.getId());

                        if (trigger.getDelayId() != null) {
                            schedulesToResetExecutionState.add(trigger.getScheduleId());
                        } else {
                            triggeredSchedules.add(trigger.getScheduleId());
                        }
                    } else {
                        if (trigger.getDelayId() != null) {
                            cancellationTriggersToIncrement.add(trigger.getId());
                        } else {
                            standardTriggersToIncrement.add(trigger.getId());
                        }
                    }

                    // Add to maps
                    if (trigger.getDelayId() != null) {
                        cancellationTriggerMap.put(trigger.getScheduleId(), trigger.getId());
                    } else {
                        standardTriggerMap.put(trigger.getScheduleId(), trigger.getId());
                    }
                }

                Set<String> deletedSchedules = new HashSet<>();
                if (!triggeredSchedules.isEmpty()) {
                    List<ActionSchedule> scheduleEntries = getSchedules(triggeredSchedules);
                    deletedSchedules = handleTriggeredSchedules(scheduleEntries);
                }

                // Don't need to waste DB time updating triggers if they'll be deleted in a schedule
                // delete propagation.
                List<String> triggersToDelete = new ArrayList<>();
                for (String id : deletedSchedules) {
                    triggersToDelete.add(standardTriggerMap.get(id));
                    if (cancellationTriggerMap.containsKey(id)) {
                        triggersToDelete.add(cancellationTriggerMap.get(id));
                    }
                }

                standardTriggersToIncrement.removeAll(triggersToDelete);
                cancellationTriggersToIncrement.removeAll(triggersToDelete);
                triggersToReset.removeAll(triggersToDelete);

                HashMap<String, List<String>> updatesMap = new HashMap<>();
                updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, value, "0"), standardTriggersToIncrement);
                updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, value, "1"), cancellationTriggersToIncrement);
                updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, triggersToReset);

                // Handle resetting schedule state.
                updatesMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_DATE_UPDATE, "-1"), new ArrayList<>(schedulesToResetExecutionState));
                updatesMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_STATE_UPDATE, "0"), new ArrayList<>(schedulesToResetExecutionState));

                dataManager.updateLists(updatesMap);

                Logger.debug("Automation - Retrieved " + triggerEntries.size() + " triggers and " + triggeredSchedules.size() + " schedules for event type " + type);
                Logger.debug("Automation - Incrementing values for " + cancellationTriggersToIncrement.size() + " cancellation triggers for event type " + type);
                Logger.debug("Automation - Resetting values for " + triggersToReset.size() + " triggers for event type " + type);
                Logger.debug("Automation - Resetting execution state for " + schedulesToResetExecutionState.size() + " schedules.");

                cancelScheduleDelays(schedulesToResetExecutionState);
            }
        });
    }

    @WorkerThread
    private Set<String> handleTriggeredSchedules(List<ActionSchedule> actionSchedules) {
        if (actionSchedules.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> schedulesToIncrement = new HashSet<>();
        Set<String> schedulesToDelete = new HashSet<>();
        Set<String> schedulesToResetExecutionState = new HashSet<>();
        Set<String> cancellationTriggersToReset = new HashSet<>();
        Set<String> schedulesPendingExecution = new HashSet<>();
        Map<Long, List<String>> schedulesToInitiateTimers = new HashMap<>();

        for (final ActionSchedule schedule : actionSchedules) {
            // Delete expired schedules.
            if (schedule.getInfo().getEnd() > 0 && schedule.getInfo().getEnd() < System.currentTimeMillis()) {
                schedulesToDelete.add(schedule.getId());
                continue;
            }

            // Ignore already triggered schedules
            if (schedule.getIsPendingExecution() && schedule.getPendingExecutionDate() > System.currentTimeMillis()) {
                continue;
            }

            // Handle schedules with delays
            if (schedule.getInfo().getDelay() != null && !schedule.getIsPendingExecution() && schedule.getInfo().getDelay().getSeconds() > 0) {
                updateListMap(schedulesToInitiateTimers, System.currentTimeMillis() + 1000*schedule.getInfo().getDelay().getSeconds(), schedule.getId());

                // Reset cancellation triggers
                cancellationTriggersToReset.add(schedule.getId());

                startDelayTimer(schedule);
                continue;
            }

            final CountDownLatch latch = new CountDownLatch(1);

            ScheduleRunnable<Boolean> runnable = new ScheduleRunnable<Boolean>(schedule.getId(), schedule.getInfo().getGroup()) {
                @Override
                protected Boolean execute() {

                    boolean isSatisfied = isScheduleConditionsSatisfied(schedule);

                    boolean actionRan = false;
                    if (isSatisfied) {
                        Bundle metadata = new Bundle();
                        metadata.putParcelable(ActionArguments.ACTION_SCHEDULE_METADATA, schedule);

                        for (Map.Entry<String, JsonValue> entry : schedule.getInfo().getActions().entrySet()) {
                            ActionRunRequest.createRequest(entry.getKey())
                                            .setValue(entry.getValue())
                                            .setSituation(Action.SITUATION_AUTOMATION)
                                            .setMetadata(metadata)
                                            .run();
                        }

                        actionRan = true;
                    }

                    return actionRan;
                }

                @Override
                public void run() {
                    super.run();
                    latch.countDown();
                }
            };

            new Handler(Looper.getMainLooper()).post(runnable);

            try {
                latch.await();
            } catch (InterruptedException ex) {
                Logger.error("Failed to execute schedule.", ex);
            }

            if (runnable.getResult()) {
                schedulesToResetExecutionState.add(schedule.getId());
                if (schedule.getCount() + 1 >= schedule.getInfo().getLimit()) {
                    schedulesToResetExecutionState.remove(schedule.getId());
                    schedulesToDelete.add(schedule.getId());
                } else {
                    schedulesToIncrement.add(schedule.getId());
                }
            } else if (!schedule.getIsPendingExecution()) {
                cancellationTriggersToReset.add(schedule.getId());
                schedulesPendingExecution.add(schedule.getId());
            }

        }

        HashMap<String, List<String>> updatesMap = new HashMap<>();
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, new ArrayList<>(schedulesToDelete));
        updatesMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, new ArrayList<>(schedulesToIncrement));
        updatesMap.put(AutomationDataManager.CANCELLATION_TRIGGERS_TO_RESET, new ArrayList<>(cancellationTriggersToReset));

        // Update execution state
        updatesMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_DATE_UPDATE, "-1"), new ArrayList<>(schedulesToResetExecutionState));
        updatesMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_STATE_UPDATE, "0"), new ArrayList<>(schedulesToResetExecutionState));
        updatesMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_STATE_UPDATE, "1"), new ArrayList<>(schedulesPendingExecution));

        Logger.debug("Automation - Deleting " + schedulesToDelete.size() + " schedules.");
        Logger.debug("Automation - Incrementing " + schedulesToIncrement.size() + " schedules.");
        Logger.debug("Automation - Resetting " + cancellationTriggersToReset.size() + " cancellation triggers.");
        Logger.debug("Automation - Resetting execution state on " + schedulesToResetExecutionState.size() + " schedules.");

        if (!schedulesToInitiateTimers.isEmpty()) {
            addSchedulesToSet(updatesMap, schedulesToInitiateTimers);
        }

        dataManager.updateLists(updatesMap);
        return schedulesToDelete;
    }

    private void startDelayTimer(ActionSchedule actionSchedule) {
        long delay = actionSchedule.getInfo().getDelay().getSeconds();
        if (delay <= 0) {
            delay = 1;
        }

        startDelayTimer(actionSchedule, delay * 1000);
    }

    private void startDelayTimer(ActionSchedule actionSchedule, long time) {
        ScheduleRunnable runnable = new ScheduleRunnable(actionSchedule.getId(), actionSchedule.getInfo().getGroup()) {
            @Override
            protected Void execute() {
                // Update before execution
                ActionSchedule schedule = getSchedule(getScheduleId());
                if (schedule != null) {
                    handleTriggeredSchedules(Collections.singletonList(schedule));
                }
                return null;
            }
        };

        this.backgroundHandler.postAtTime(runnable, actionSchedule.getId(), time + SystemClock.uptimeMillis());
        this.delayedRunnables.add(runnable);
    }

    @WorkerThread
    private boolean isScheduleConditionsSatisfied(ActionSchedule schedule) {
        ScheduleDelay delay = schedule.getInfo().getDelay();

        if (delay == null) {
            return true;
        }

        if (schedule.getPendingExecutionDate() > System.currentTimeMillis()) {
            return false;
        }

        if (delay.getScreen() != null && !delay.getScreen().equals(screen)) {
            return false;
        }

        if (delay.getRegionId() != null && !delay.getRegionId().equals(regionId)) {
            return false;
        }

        switch (delay.getAppState()) {
            case ScheduleDelay.APP_STATE_FOREGROUND:
                if (!isForeground) {
                    return false;
                }

                break;

            case ScheduleDelay.APP_STATE_BACKGROUND:
                if (isForeground) {
                    return false;
                }

                break;
        }

        return true;
    }


    /**
     * Runs a {@link com.urbanairship.PendingResult.ResultCallback} instance for a given result. The
     * callback is posted to the thread's looper, and will default to the main looper if one doesn't exist.
     *
     * @param callback The callback.
     * @param result The result.
     * @param looper The looper to which the callback is posted.
     * @param <T> The result type.
     */
    private <T> void runCallback(@Nullable final PendingResult.ResultCallback<T> callback, @Nullable final T result, Looper looper) {
        if (callback != null) {
            Handler handler = new Handler(looper);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(result);
                }
            });
        }
    }

    void addSchedulesToSet(Map<String, List<String>> queryMap, Map<Long, List<String>> schedulesToSet) {
        if (!schedulesToSet.isEmpty()) {
            for (Map.Entry<Long, List<String>> entry : schedulesToSet.entrySet()) {
                queryMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_STATE_UPDATE, "1"), entry.getValue());
                queryMap.put(String.format(AutomationDataManager.SCHEDULES_EXECUTION_DATE_UPDATE, entry.getKey()), entry.getValue());
            }
        }
    }

    void updateListMap(Map<Long, List<String>> listMap, Long time, String scheduleId) {
        if (listMap.containsKey(time)) {
            listMap.get(time).add(scheduleId);
        } else {
            ArrayList<String> initialList = new ArrayList<>(Arrays.asList(scheduleId));
            listMap.put(time, initialList);
        }
    }

    private abstract class ScheduleRunnable<T> implements Runnable {
        private String scheduleId;
        private String group;
        private T result;

        ScheduleRunnable(String scheduleId, String group) {
            this.scheduleId = scheduleId;
            this.group = group;
        }

        @Override
        public void run() {
            result = execute();
        }

        protected abstract T execute();

        public T getResult() {
            return result;
        }

        public String getScheduleId() {
            return scheduleId;
        }
    }
}
