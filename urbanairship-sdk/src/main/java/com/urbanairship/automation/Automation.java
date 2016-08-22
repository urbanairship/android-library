/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.LocalBroadcastManager;

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
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.RegionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This class is the primary interface to the Urban Airship On Device Automation API. If accessed outside
 * of the main process, the class methods will no-op.
 */
public class Automation extends AirshipComponent {

    private static final String KEY_PREFIX = "com.urbanairship.automation";
    private static final String AUTOMATION_ENABLED_KEY = KEY_PREFIX + ".AUTOMATION_ENABLED";

    private final Context context;
    private final AutomationDataManager dataManager;
    private final Executor eventProcessingExecutor = Executors.newSingleThreadExecutor();
    private final Executor dbRequestProcessingExecutor = Executors.newCachedThreadPool();
    private final PreferenceDataStore preferenceDataStore;

    private final Analytics analytics;

    private BroadcastReceiver broadcastReceiver;
    private AnalyticsListener analyticsListener;

    private boolean automationEnabled = false;

    /**
     * Automation schedules limit.
     */
    public static final long SCHEDULES_LIMIT = 1000;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @param analytics The analytics instance.
     * @hide
     */
    public Automation(@NonNull Context context, @NonNull AirshipConfigOptions configOptions, @NonNull Analytics analytics, @NonNull PreferenceDataStore preferenceDataStore) {
        this(context, analytics, new AutomationDataManager(context, configOptions.getAppKey()), preferenceDataStore);
    }

    Automation(@NonNull Context context, @NonNull Analytics analytics, @NonNull AutomationDataManager dataManager, @NonNull PreferenceDataStore preferenceDataStore) {
        this.context = context;
        this.analytics = analytics;
        this.dataManager = dataManager;
        this.preferenceDataStore = preferenceDataStore;
    }

    @Override
    protected void init() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        if (broadcastReceiver == null) {
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(Analytics.ACTION_APP_BACKGROUND)) {
                        onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_BACKGROUND, 1.00);
                    } else {
                        onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_FOREGROUND, 1.00);
                    }
                }
            };
        }

        if (analyticsListener == null) {
            analyticsListener = new AnalyticsListener() {
                @Override
                public void onRegionEventAdded(RegionEvent regionEvent) {
                    if (regionEvent.getBoundaryEvent() == RegionEvent.BOUNDARY_EVENT_ENTER) {
                        onEventAdded(JsonValue.wrapOpt(regionEvent.getRegionId()), Trigger.REGION_ENTER, 1.00);
                    } else {
                        onEventAdded(JsonValue.wrapOpt(regionEvent.getRegionId()), Trigger.REGION_EXIT, 1.00);
                    }
                }

                @Override
                public void onCustomEventAdded(CustomEvent customEvent) {
                    JsonMap.Builder data = JsonMap.newBuilder()
                                                  .put(CustomEvent.EVENT_NAME, customEvent.getEventName())
                                                  .put(CustomEvent.INTERACTION_ID, customEvent.getInteractionId())
                                                  .put(CustomEvent.INTERACTION_TYPE, customEvent.getInteractionType())
                                                  .put(CustomEvent.TRANSACTION_ID, customEvent.getTransactionId())
                                                  .put(CustomEvent.PROPERTIES, JsonValue.wrapOpt(customEvent.getProperties()));

                    if (customEvent.getEventValue() != null) {
                        data.put(CustomEvent.EVENT_VALUE, customEvent.getEventValue().doubleValue());
                        onEventAdded(data.build(), Trigger.CUSTOM_EVENT_VALUE, customEvent.getEventValue().doubleValue());
                    }

                    onEventAdded(data.build(), Trigger.CUSTOM_EVENT_COUNT, 1.00);
                }

                @Override
                public void onScreenTracked(String screenName) {
                    onEventAdded(JsonValue.wrap(screenName), Trigger.SCREEN_VIEW, 1.00);
                }
            };
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Analytics.ACTION_APP_FOREGROUND);
        filter.addAction(Analytics.ACTION_APP_BACKGROUND);

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        broadcastManager.registerReceiver(broadcastReceiver, filter);

        analytics.addAnalyticsListener(analyticsListener);
        automationEnabled = preferenceDataStore.getBoolean(AUTOMATION_ENABLED_KEY, false);
    }

    @Override
    protected void tearDown() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver);
        analytics.removeAnalyticsListener(analyticsListener);
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
     * if the scheduling failed or the schedule count is greater than or equal
     * to {@link #SCHEDULES_LIMIT}.
     */
    public void scheduleAsync(final ActionScheduleInfo scheduleInfo, @Nullable final PendingResult.ResultCallback<ActionSchedule> callback) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dbRequestProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                ActionSchedule schedule = schedule(scheduleInfo);

                if (callback != null) {
                    callback.onResult(schedule);
                }
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
     * {@link Collections#emptyList()} if the scheduling failed or the schedule count is greater than or equal
     * to {@link #SCHEDULES_LIMIT}.
     */
    public void scheduleAsync(final List<ActionScheduleInfo> scheduleInfos, final PendingResult.ResultCallback<List<ActionSchedule>> callback) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dbRequestProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<ActionSchedule> schedule = schedule(scheduleInfos);

                if (callback != null) {
                    callback.onResult(schedule);
                }
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

        dbRequestProcessingExecutor.execute(new Runnable() {
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

        dbRequestProcessingExecutor.execute(new Runnable() {
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

        dbRequestProcessingExecutor.execute(new Runnable() {
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
    }

    /**
     * Cancels all schedules asynchronously.
     */
    public void cancelAllAsync() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dbRequestProcessingExecutor.execute(new Runnable() {
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
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation.
     */
    public void getScheduleAsync(final String id, final PendingResult.ResultCallback<ActionSchedule> callback) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dbRequestProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                ActionSchedule schedule = getSchedule(id);

                if (callback != null) {
                    callback.onResult(schedule);
                }
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
     * Gets all schedules asynchronously.
     *
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation.
     */
    public void getSchedulesAsync(final PendingResult.ResultCallback<List<ActionSchedule>> callback) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dbRequestProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<ActionSchedule> schedule = getSchedules();

                if (callback != null) {
                    callback.onResult(schedule);
                }
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
     * @param callback An {@link com.urbanairship.PendingResult.ResultCallback} implementation.
     */
    public void getSchedulesAsync(final String group, final PendingResult.ResultCallback<List<ActionSchedule>> callback) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }

        dbRequestProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<ActionSchedule> schedule = getSchedules(group);

                if (callback != null) {
                    callback.onResult(schedule);
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
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process, canceling operation.");
            return;
        }
        
        if (!automationEnabled) {
            return;
        }

        Logger.debug("Automation - updating triggers with type: " + type);

        eventProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<TriggerEntry> triggerEntries = dataManager.getTriggers(type);

                if (triggerEntries.isEmpty()) {
                    return;
                }

                List<String> triggersToIncrement = new ArrayList<>();
                List<String> triggersToReset = new ArrayList<>();
                // Schedule ID to triggers map
                Map<String, String> triggerMap = new HashMap<>();

                Set<String> schedulesToIncrement = new HashSet<>();
                Set<String> schedulesToDelete = new HashSet<>();
                Set<String> triggeredSchedules = new HashSet<>();

                for (TriggerEntry trigger : triggerEntries) {
                    if ((json != null && (trigger.getPredicate() != null && !trigger.getPredicate().apply(json)))) {
                        continue;
                    }

                    double progress = trigger.getProgress() + value;
                    if (progress >= trigger.getGoal()) {
                        triggersToReset.add(trigger.getId());
                        triggeredSchedules.add(trigger.getScheduleId());
                    } else {
                        triggersToIncrement.add(trigger.getId());
                    }

                    triggerMap.put(trigger.getScheduleId(), trigger.getId());
                }

                if (!triggeredSchedules.isEmpty()) {
                    List<ActionSchedule> scheduleEntries = dataManager.getSchedules(triggeredSchedules);

                    for (ActionSchedule schedule : scheduleEntries) {
                        if (schedule.getInfo().getEnd() > 0 && schedule.getInfo().getEnd() < System.currentTimeMillis()) {
                            schedulesToDelete.add(schedule.getId());
                            continue;
                        }

                        Bundle metadata = new Bundle();
                        metadata.putParcelable(ActionArguments.ACTION_SCHEDULE_METADATA, schedule);

                        for (Map.Entry<String, JsonValue> entry : schedule.getInfo().getActions().entrySet()) {
                            ActionRunRequest.createRequest(entry.getKey())
                                            .setValue(entry.getValue())
                                            .setSituation(Action.SITUATION_AUTOMATION)
                                            .setMetadata(metadata)
                                            .run();
                        }

                        if (schedule.getCount() + 1 >= schedule.getInfo().getLimit()) {
                            schedulesToDelete.add(schedule.getId());
                        } else {
                            schedulesToIncrement.add(schedule.getId());
                        }
                    }
                }

                HashMap<String, List<String>> updatesMap = new HashMap<>();
                updatesMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, new ArrayList<>(schedulesToDelete));
                updatesMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, new ArrayList<>(schedulesToIncrement));

                // Don't need to waste DB time updating triggers if they'll be deleted in a schedule
                // delete propagation.
                List<String> triggersToDelete = new ArrayList<>();
                for (String id : schedulesToDelete) {
                    triggersToDelete.add(triggerMap.get(id));
                }

                triggersToIncrement.removeAll(triggersToDelete);
                triggersToReset.removeAll(triggersToDelete);

                updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, value), triggersToIncrement);
                updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, triggersToReset);

                Logger.debug("Automation - Retrieved " + triggerEntries.size() + " triggers and " + triggeredSchedules.size() + " schedules for event type " + type);
                Logger.debug("Automation - Incrementing " + schedulesToIncrement.size() + " schedules for event type " + type);
                Logger.debug("Automation - Deleting " + schedulesToDelete.size() + " schedules for event type " + type);
                Logger.debug("Automation - Updating values for " + triggersToIncrement.size() + " triggers for event type " + type);
                Logger.debug("Automation - Resetting values for " + triggersToReset.size() + " triggers for event type " + type);

                dataManager.updateLists(updatesMap);
            }
        });
    }

}
