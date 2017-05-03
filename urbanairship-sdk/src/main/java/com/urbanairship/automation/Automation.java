/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.MainThread;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private Handler backgroundHandler;

    @VisibleForTesting
    final HandlerThread backgroundThread;

    private AnalyticsListener analyticsListener;

    private boolean automationEnabled = false;
    private List<ScheduleRunnable<Void>> delayedRunnables = new ArrayList<>();

    private String screen;
    private String regionId;

    /**
     * Automation schedules limit.
     */
    public static final long SCHEDULES_LIMIT = 100;

    /**
     * Default constructor.
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
                Automation.this.onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_FOREGROUND, 1.00);
                onScheduleConditionsChanged();
            }

            @Override
            public void onBackground(long time) {
                Automation.this.onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_BACKGROUND, 1.00);
                onScheduleConditionsChanged();
            }
        };

        this.activityMonitor = activityMonitor;
        this.backgroundThread = new HandlerThread("delayed");
    }

    @Override
    protected void init() {
        if (!UAirship.isMainProcess()) {
            return;
        }

        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());

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

        onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_APP_INIT, 1.00);

        onScheduleConditionsChanged();
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

        List<ActionSchedule> scheduleList = schedule(Collections.singletonList(scheduleInfo));
        if (scheduleList != null && !scheduleList.isEmpty()) {
            return scheduleList.get(0);
        }

        return null;
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

        if (dataManager.getScheduleCount() + scheduleInfos.size() > SCHEDULES_LIMIT) {
            Logger.error("AutomationDataManager - unable to insert schedule due to schedule exceeded limit.");
            return Collections.emptyList();
        }

        List<ScheduleEntry> entries = new ArrayList<>();
        List<ActionSchedule> schedules = new ArrayList<>();

        for (ActionScheduleInfo info : scheduleInfos) {
            ActionSchedule schedule = new ActionSchedule(UUID.randomUUID().toString(), info);
            schedules.add(schedule);
            entries.add(new ScheduleEntry(schedule));
        }

        if (!automationEnabled) {
            automationEnabled = true;
            preferenceDataStore.put(AUTOMATION_ENABLED_KEY, true);
        }

        dataManager.saveSchedules(entries);

        return schedules;
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

        dataManager.deleteSchedules(ids);
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

        dataManager.deleteGroup(group);
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

        dataManager.deleteAllSchedules();
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

        Set<String> hashSet = new HashSet<>();
        hashSet.add(id);

        List<ActionSchedule> schedules = getSchedules(hashSet);
        if (!schedules.isEmpty()) {
            return schedules.get(0);
        }

        return null;
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

        List<ScheduleEntry> entries = dataManager.getScheduleEntries();
        List<ActionSchedule> schedules = new ArrayList<>(entries.size());
        for (ScheduleEntry entry : entries) {
            schedules.add(entry.toSchedule());
        }

        return schedules;
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

        List<ScheduleEntry> entries = dataManager.getScheduleEntries();
        List<ActionSchedule> schedules = new ArrayList<>(entries.size());
        for (ScheduleEntry entry : entries) {
            schedules.add(entry.toSchedule());
        }

        return schedules;
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

        List<ScheduleEntry> entries = dataManager.getScheduleEntries(group);
        List<ActionSchedule> schedules = new ArrayList<>(entries.size());
        for (ScheduleEntry entry : entries) {
            schedules.add(entry.toSchedule());
        }

        return schedules;
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
     * Cancel delayed schedule runnables.
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

    /**
     * Cancels all delayed schedule runnables.
     */
    private void cancelAllDelays() {
        synchronized (delayedRunnables) {
            for (ScheduleRunnable runnable : delayedRunnables) {
                backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
            }

            delayedRunnables.clear();
        }
    }

    /**
     * Reschedule all delay schedule runnables.
     */
    @WorkerThread
    private void rescheduleDelays() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {

                List<ScheduleEntry> scheduleEntries = dataManager.getPendingExecutionSchedules();
                if (scheduleEntries.isEmpty()) {
                    return;
                }

                List<ScheduleEntry> schedulesToUpdate = new ArrayList<>();

                for (ScheduleEntry scheduleEntry : schedulesToUpdate) {
                    // No delay, mark it to be executed
                    if (scheduleEntry.seconds == 0) {
                        continue;
                    }

                    long delay = TimeUnit.SECONDS.toMillis(scheduleEntry.seconds);
                    long remainingDelay = scheduleEntry.getPendingExecutionDate() - System.currentTimeMillis();

                    if (remainingDelay <= 0) {
                        continue;
                    }

                    // If remaining delay is greater than the original delay, reset the delay.
                    if (remainingDelay > delay) {
                        scheduleEntry.setPendingExecutionDate(System.currentTimeMillis() + delay);
                        schedulesToUpdate.add(scheduleEntry);
                        remainingDelay = delay;
                    }

                    startDelayTimer(scheduleEntry, remainingDelay);
                }
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
                List<ScheduleEntry> scheduleEntries = dataManager.getPendingExecutionSchedules();
                if (scheduleEntries.isEmpty()) {
                    return;
                }


                handleTriggeredSchedules(scheduleEntries);
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
                List<TriggerEntry> triggerEntries = dataManager.getActiveTriggerEntries(type);
                if (triggerEntries.isEmpty()) {
                    return;
                }

                Set<String> triggeredSchedules = new HashSet<>();
                Set<String> cancelledSchedules = new HashSet<>();

                Map<String, List<TriggerEntry>> triggersToUpdate = new HashMap<>();

                for (TriggerEntry trigger : triggerEntries) {
                    if ((json != null && (trigger.jsonPredicate != null && !trigger.jsonPredicate.apply(json)))) {
                        continue;
                    }

                    if (!triggersToUpdate.containsKey(trigger.scheduleId)) {
                        triggersToUpdate.put(trigger.scheduleId, new ArrayList<TriggerEntry>());
                    }

                    triggersToUpdate.get(trigger.scheduleId).add(trigger);

                    trigger.setProgress(trigger.getProgress() + value);

                    if (trigger.getProgress() >= trigger.goal) {
                        trigger.setProgress(0);

                        if (trigger.isCancellation) {
                            cancelledSchedules.add(trigger.scheduleId);
                            cancelScheduleDelays(Collections.singletonList(trigger.scheduleId));
                        } else {
                            triggeredSchedules.add(trigger.scheduleId);
                        }
                    }
                }

                if (!cancelledSchedules.isEmpty()) {
                    triggeredSchedules.removeAll(cancelledSchedules);
                    List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(cancelledSchedules);
                    for (ScheduleEntry entry : scheduleEntries) {
                        entry.setPendingExecutionDate(-1);
                        entry.setIsPendingExecution(false);
                    }

                    dataManager.saveSchedules(scheduleEntries);
                }

                if (!triggeredSchedules.isEmpty()) {
                    List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(triggeredSchedules);

                    for (String deletedSchedule : handleTriggeredSchedules(scheduleEntries)) {
                        triggersToUpdate.remove(deletedSchedule);
                    }
                }

                List<TriggerEntry> updatedTriggers = new ArrayList<>();
                for (Map.Entry<String, List<TriggerEntry>> entry : triggersToUpdate.entrySet()) {
                    updatedTriggers.addAll(entry.getValue());
                }

                dataManager.saveTriggers(updatedTriggers);
            }
        });
    }

    /**
     * Processes a list of triggered schedule entries.
     *
     * @param scheduleEntries A list of triggered schedule entries.
     * @return A set of deleted schedule IDs.
     */
    @WorkerThread
    private Set<String> handleTriggeredSchedules(List<ScheduleEntry> scheduleEntries) {
        if (scheduleEntries.isEmpty()) {
            return new HashSet<>();
        }

        HashSet<String> schedulesToDelete = new HashSet<>();
        HashSet<ScheduleEntry> schedulesToUpdate = new HashSet<>();

        for (final ScheduleEntry scheduleEntry : scheduleEntries) {
            // Delete expired schedules.
            if (scheduleEntry.end > 0 && scheduleEntry.end < System.currentTimeMillis()) {
                schedulesToDelete.add(scheduleEntry.scheduleId);
                continue;
            }

            // Ignore already triggered schedules
            if (scheduleEntry.isPendingExecution() && scheduleEntry.getPendingExecutionDate() > System.currentTimeMillis()) {
                continue;
            }

            // Handle schedules with delays
            if (!scheduleEntry.isPendingExecution() && scheduleEntry.seconds > 0) {

                for (TriggerEntry triggerEntry : scheduleEntry.triggers) {
                    if (triggerEntry.isCancellation) {
                        triggerEntry.setProgress(0);
                    }
                }

                scheduleEntry.setIsPendingExecution(true);
                scheduleEntry.setPendingExecutionDate(TimeUnit.SECONDS.toMillis(scheduleEntry.seconds) + System.currentTimeMillis());
                startDelayTimer(scheduleEntry, TimeUnit.SECONDS.toMillis(scheduleEntry.seconds));
                schedulesToUpdate.add(scheduleEntry);
                continue;
            }

            final CountDownLatch latch = new CountDownLatch(1);

            ScheduleRunnable<Boolean> runnable = new ScheduleRunnable<Boolean>(scheduleEntry.scheduleId, scheduleEntry.group) {

                @Override
                public void run() {
                    result = isScheduleConditionsSatisfied(scheduleEntry);
                    latch.countDown();

                    if (result) {

                        ActionSchedule schedule = scheduleEntry.toSchedule();
                        Bundle metadata = new Bundle();
                        metadata.putParcelable(ActionArguments.ACTION_SCHEDULE_METADATA, schedule);

                        for (Map.Entry<String, JsonValue> entry : schedule.getInfo().getActions().entrySet()) {
                            ActionRunRequest.createRequest(entry.getKey())
                                            .setValue(entry.getValue())
                                            .setSituation(Action.SITUATION_AUTOMATION)
                                            .setMetadata(metadata)
                                            .run();
                        }
                    }

                }
            };

            new Handler(Looper.getMainLooper()).post(runnable);

            try {
                latch.await();
            } catch (InterruptedException ex) {
                Logger.error("Failed to execute schedule. ", ex);
            }

            if (runnable.result) {
                scheduleEntry.setIsPendingExecution(false);
                scheduleEntry.setPendingExecutionDate(-1);
                scheduleEntry.setCount(scheduleEntry.getCount() + 1);

                if (scheduleEntry.getCount() >= scheduleEntry.limit) {
                    schedulesToDelete.add(scheduleEntry.scheduleId);
                } else {
                    schedulesToUpdate.add(scheduleEntry);
                }
            } else if (!scheduleEntry.isPendingExecution()) {
                for (TriggerEntry triggerEntry : scheduleEntry.triggers) {
                    if (triggerEntry.isCancellation) {
                        triggerEntry.setProgress(0);
                    }
                }
                scheduleEntry.setIsPendingExecution(true);
                schedulesToUpdate.add(scheduleEntry);
            }
        }

        dataManager.saveSchedules(schedulesToUpdate);
        dataManager.deleteSchedules(schedulesToDelete);
        return schedulesToDelete;
    }

    /**
     * Starts a delay timer for a schedule entry.
     *
     * @param scheduleEntry The schedule entry.
     * @param delay The delay in milliseconds.
     */
    private void startDelayTimer(ScheduleEntry scheduleEntry, long delay) {
        ScheduleRunnable runnable = new ScheduleRunnable(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            public void run() {
                // Update before execution
                Set<String> hashSet = new HashSet<>();
                hashSet.add(scheduleId);
                List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(hashSet);

                handleTriggeredSchedules(scheduleEntries);
            }
        };

        this.backgroundHandler.postAtTime(runnable, scheduleEntry.scheduleId, SystemClock.uptimeMillis() + delay);
        this.delayedRunnables.add(runnable);
    }

    /**
     * Checks if the schedule entry's conditions are met.
     *
     * @param scheduleEntry The schedule entry.
     * @return {@code true} if the conditions are met, otherwise {@code false}.
     */
    @MainThread
    private boolean isScheduleConditionsSatisfied(ScheduleEntry scheduleEntry) {

        if (scheduleEntry.getPendingExecutionDate() > System.currentTimeMillis()) {
            return false;
        }

        if (scheduleEntry.screen != null && !scheduleEntry.screen.equals(screen)) {
            return false;
        }

        if (scheduleEntry.regionId != null && !scheduleEntry.regionId.equals(regionId)) {
            return false;
        }

        switch (scheduleEntry.appState) {
            case ScheduleDelay.APP_STATE_FOREGROUND:
                if (!activityMonitor.isAppForegrounded()) {
                    return false;
                }

                break;

            case ScheduleDelay.APP_STATE_BACKGROUND:
                if (activityMonitor.isAppForegrounded()) {
                    return false;
                }

                break;

            case ScheduleDelay.APP_STATE_ANY:
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
    
    private abstract class ScheduleRunnable<T> implements Runnable {
        final String scheduleId;
        final String group;
        T result;

        ScheduleRunnable(String scheduleId, String group) {
            this.scheduleId = scheduleId;
            this.group = group;
        }

    }
}
