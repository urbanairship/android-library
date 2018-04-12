/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.AlarmOperationScheduler;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * This class is the primary interface to the Urban Airship On Device Automation API. If accessed outside
 * of the main process, the class methods will no-op.
 */
public class Automation extends AirshipComponent {

    /**
     * The Action Automation data store.
     */
    private static final String DATABASE_NAME = "ua_automation.db";

    private AutomationEngine<ActionSchedule> automationEngine;

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
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Automation(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                      @NonNull AirshipConfigOptions configOptions, @NonNull Analytics analytics,
                      @NonNull ActivityMonitor activityMonitor) {
        super(preferenceDataStore);

        this.automationEngine = new AutomationEngine.Builder<ActionSchedule>()
                .setScheduleLimit(SCHEDULES_LIMIT)
                .setActivityMonitor(activityMonitor)
                .setAnalytics(analytics)
                .setDriver(new ActionAutomationDriver())
                .setDataManager(new AutomationDataManager(context, configOptions.getAppKey(), DATABASE_NAME))
                .setOperationScheduler(AlarmOperationScheduler.shared(context))
                .build();
    }

    @Override
    protected void init() {
        super.init();

        if (!UAirship.isMainProcess()) {
            return;
        }

        automationEngine.start();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onComponentEnableChange(boolean isEnabled) {
        if (!UAirship.isMainProcess()) {
            return;
        }

        automationEngine.setPaused(!isEnabled);
    }

    @Override
    protected void tearDown() {
        if (!UAirship.isMainProcess()) {
            return;
        }

        automationEngine.stop();
    }

    /**
     * Schedules a single action schedule.
     *
     * @param scheduleInfo The {@link ActionScheduleInfo} instance.
     * @return A pending result.
     */
    public PendingResult<ActionSchedule> schedule(@NonNull final ActionScheduleInfo scheduleInfo) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return new PendingResult<>();
        }

        return automationEngine.schedule(scheduleInfo);
    }

    /**
     * Schedules a list of action schedules.
     *
     * @param scheduleInfos A list of {@link ActionScheduleInfo}.
     * @return A pending result.
     */
    public PendingResult<List<ActionSchedule>> schedule(@NonNull final List<ActionScheduleInfo> scheduleInfos) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return new PendingResult<>();
        }

        return automationEngine.schedule(scheduleInfos);
    }


    /**
     * Cancels a schedule for a given schedule ID.
     *
     * @param id The schedule ID.
     * @return A pending result.
     */
    public Future<Void> cancel(@NonNull final String id) {
        return cancel(Collections.singletonList(id));
    }


    /**
     * Cancels schedules for a given list of schedule IDs.
     *
     * @param ids The list of schedule IDs.
     * @return A pending result.
     */
    public Future<Void> cancel(@NonNull final Collection<String> ids) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return new PendingResult<>();
        }

        return automationEngine.cancel(ids);
    }


    /**
     * Cancels a group of schedules.
     *
     * @param group The schedule group.
     * @return A pending result.
     */
    public PendingResult<Boolean> cancelGroup(@NonNull final String group) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            PendingResult<Boolean> pendingResult = new PendingResult<>();
            pendingResult.setResult(false);
            return pendingResult;
        }

        return automationEngine.cancelGroup(group);
    }

    /**
     * Cancels all schedules.
     *
     * @return A pending result.
     */
    public Future<Void> cancelAll() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return new PendingResult<>();
        }

        return automationEngine.cancelAll();
    }


    /**
     * Gets a schedule for the given schedule ID.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    public PendingResult<ActionSchedule> getSchedule(@NonNull final String scheduleId) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return null;
        }

        return automationEngine.getSchedule(scheduleId);
    }


    /**
     * Gets a list of schedules with the given IDs.
     *
     * @param scheduleIds The requested schedule IDs.
     * @return A pending result.
     */
    public PendingResult<Collection<ActionSchedule>> getSchedules(@NonNull final Set<String> scheduleIds) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return null;
        }

        return automationEngine.getSchedules(scheduleIds);
    }

    /**
     * Gets all the schedules.
     *
     * @return A pending result.
     */
    public PendingResult<Collection<ActionSchedule>> getSchedules() {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return null;
        }

        return automationEngine.getSchedules();
    }

    /**
     * Gets all schedules for a given group.
     *
     * @param group The group.
     * @return A pending result.
     */
    public PendingResult<Collection<ActionSchedule>> getSchedules(final String group) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return null;
        }

        return automationEngine.getSchedules(group);
    }

    /**
     * Edits an in-app message schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The edits.
     * @return A pending result with the updated schedule. The schedule will be null if it does not exist.
     */
    PendingResult<ActionSchedule> editSchedule(@NonNull String scheduleId, @NonNull ActionScheduleEdits edits) {
        if (!UAirship.isMainProcess()) {
            Logger.warn("Automation - Cannot access the Automation API outside of the main process");
            return null;
        }

        return automationEngine.editSchedule(scheduleId, edits);
    }
}
