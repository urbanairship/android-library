/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AnalyticsListener;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.RegionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This class is the primary interface to the Urban Airship On Device Automation API.
 */
public class Automation extends AirshipComponent {

    private final Context context;
    private final AutomationDataManager dataManager;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Analytics analytics;

    private BroadcastReceiver broadcastReceiver;
    private AnalyticsListener analyticsListener;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @param analytics The analytics instance.
     * @hide
     */
    public Automation(@NonNull Context context, AirshipConfigOptions configOptions, Analytics analytics) {
        this(context, analytics, new AutomationDataManager(context, configOptions.getAppKey()));
    }

    Automation(@NonNull Context context, Analytics analytics, AutomationDataManager dataManager) {
        this.context = context;
        this.analytics = analytics;
        this.dataManager = dataManager;
    }

    @Override
    protected void init() {
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
                        onEventAdded(JsonValue.wrapOpt(regionEvent.getEventId()), Trigger.REGION_ENTER, 1.00);
                    } else {
                        onEventAdded(JsonValue.wrapOpt(regionEvent.getEventId()), Trigger.REGION_EXIT, 1.00);
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
                        data.put(CustomEvent.EVENT_VALUE, customEvent.getEventValue().toString());
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

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        broadcastManager.registerReceiver(broadcastReceiver, filter);

        analytics.addAnalyticsListener(analyticsListener);
    }

    @Override
    protected void tearDown() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver);
        analytics.removeAnalyticsListener(analyticsListener);
    }

    /**
     * Schedules an {@link ActionScheduleInfo} instance.
     *
     * @param scheduleInfo The {@link ActionScheduleInfo} instance.
     * @return The scheduled {@link ActionSchedule} containing the relevant
     * {@link ActionScheduleInfo} and generated schedule ID.
     */
    @WorkerThread
    public ActionSchedule schedule(ActionScheduleInfo scheduleInfo) {
        ActionSchedule actionSchedule = dataManager.insertSchedule(scheduleInfo);
        Logger.debug("Automation - action schedule inserted: " + actionSchedule.toString());
        return actionSchedule;
    }

    /**
     * Schedules a list of {@link ActionScheduleInfo} instances.
     *
     * @param scheduleInfos The list of {@link ActionScheduleInfo} instances.
     * @return The list of scheduled {@link ActionSchedule} instances, each containing the relevant
     * {@link ActionScheduleInfo} and generated schedule ID.
     */
    @WorkerThread
    public List<ActionSchedule> schedule(List<ActionScheduleInfo> scheduleInfos) {
        List<ActionSchedule> actionSchedules = dataManager.bulkInsertSchedules(scheduleInfos);
        for (ActionSchedule actionSchedule : actionSchedules) {
            Logger.debug("Automation - action schedule inserted: " + actionSchedule.toString());
        }
        return actionSchedules;
    }

    /**
     * Cancels a schedule for a given schedule ID.
     *
     * @param id The schedule ID.
     */
    @WorkerThread
    public void cancel(String id) {
        dataManager.deleteSchedule(id);
    }

    /**
     * Cancels schedules for a given list of schedule IDs.
     *
     * @param ids The list of schedule IDs.
     */
    @WorkerThread
    public void cancel(List<String> ids) {
        dataManager.bulkDeleteSchedules(ids);
    }

    /**
     * Cancels a group of schedules.
     *
     * @param group The schedule group.
     */
    @WorkerThread
    public void cancelGroup(String group) {
        dataManager.deleteSchedules(group);
    }

    /**
     * Cancels all schedules.
     */
    @WorkerThread
    public void cancelAll() {
        dataManager.deleteSchedules();
    }

    /**
     * Gets a schedule for a given schedule ID.
     *
     * @param id The schedule ID.
     * @return The retrieved {@link ActionSchedule}.
     */
    @WorkerThread
    public ActionSchedule getSchedule(String id) {
        return dataManager.getSchedule(id);
    }

    /**
     * Gets all schedules.
     *
     * @return The list of retrieved {@link ActionSchedule} instances.
     */
    @WorkerThread
    public List<ActionSchedule> getSchedules() {
        return dataManager.getSchedules();
    }


    /**
     * Gets all schedules for a given group.
     *
     * @param group The group.
     * @return The list of retrieved {@link ActionSchedule} instances.
     */
    @WorkerThread
    public List<ActionSchedule> getSchedules(String group) {
        return dataManager.getSchedules(group);
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
        Logger.debug("Automation - updating triggers with type: " + type);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<TriggerEntry> triggerEntries = dataManager.getTriggers(type);

                if (triggerEntries == null) {
                    return;
                }

                long triggerCount = triggerEntries.size();

                List<String> triggersToIncrement = new ArrayList<>();
                List<String> triggersToReset = new ArrayList<>();
                List<String> schedulesToIncrement = new ArrayList<>();
                List<String> schedulesToDelete = new ArrayList<>();
                Set<String> readySchedules = new HashSet<>();

                for (TriggerEntry trigger : triggerEntries) {
                    if ((json != null && (trigger.getPredicate() != null && !trigger.getPredicate().apply(json))) || trigger.getStart() > System.currentTimeMillis()) {
                        continue;
                    }

                    double progress = trigger.getProgress() + value;
                    if (progress >= trigger.getGoal()) {
                        triggersToReset.add(trigger.getId());
                        readySchedules.add(trigger.getScheduleId());
                    } else {
                        triggersToIncrement.add(trigger.getId());
                    }
                }

                long scheduleCount = 0;
                if (!readySchedules.isEmpty()) {
                    Logger.debug("Automation - schedules to run actions for " + readySchedules);
                    List<ActionSchedule> scheduleEntries = dataManager.getSchedules(readySchedules);

                    scheduleCount = scheduleEntries.size();

                    for (ActionSchedule schedule : scheduleEntries) {
                        Logger.debug("Automation - running actions for schedule: " + schedule);

                        if (schedule.getInfo().getEnd() > 0 && schedule.getInfo().getEnd() < System.currentTimeMillis()) {
                            schedulesToDelete.add(schedule.getId());
                            continue;
                        }

                        for (ActionRunRequest actionRunRequest : schedule.getInfo().getActionRunRequests()) {
                            actionRunRequest.run();
                        }

                        if (schedule.getCount() + 1 >= schedule.getInfo().getLimit()) {
                            schedulesToDelete.add(schedule.getId());
                        } else {
                            schedulesToIncrement.add(schedule.getId());
                        }
                    }
                }

                Logger.debug("Automation - Retrieved " + triggerCount + " triggers and " + scheduleCount + " schedules for event type " + type);

                HashMap<String, List<String>> updatesMap = new HashMap<>();
                updatesMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, schedulesToDelete);
                updatesMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, schedulesToIncrement);
                updatesMap.put(String.format(AutomationDataManager.TRIGGERS_TO_INCREMENT_QUERY, value), triggersToIncrement);
                updatesMap.put(AutomationDataManager.TRIGGERS_TO_RESET_QUERY, triggersToReset);

                Logger.debug("Automation - Incrementing " + schedulesToIncrement.size() + " schedules for event type " + type);
                Logger.debug("Automation - Deleting " + schedulesToDelete.size() + " schedules for event type " + type);
                Logger.debug("Automation - Updating values for " + triggersToIncrement.size() + " triggers for event type " + type);
                Logger.debug("Automation - Resetting values for " + triggersToReset.size() + " triggers for event type " + type);

                dataManager.updateLists(updatesMap);
            }
        });
    }

}
