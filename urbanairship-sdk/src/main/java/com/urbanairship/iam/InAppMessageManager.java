/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDataManager;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.iam.banner.BannerDisplayAdapter;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * In-app messaging manager.
 */
public class InAppMessageManager extends AirshipComponent {

    /**
     * Default delay between displaying in-app messages.
     */
    private static final long MESSAGE_DISPLAY_INTERVAL_MS = 5000;

    /**
     * Metadata an app can use to prevent an in-app message from showing on a specific activity.
     */
    public final static String EXCLUDE_FROM_AUTO_SHOW = "com.urbanairship.push.iam.EXCLUDE_FROM_AUTO_SHOW";

    // State
    private String currentScheduleId;
    private WeakReference<Activity> resumedActivity;
    private WeakReference<Activity> currentActivity;
    private Stack<String> carryOverScheduleIds = new Stack<>();
    private Map<String, DisplayWrapper> displayWrapperMap = new HashMap<>();
    private boolean isDisplayedLocked = false;

    private final ActivityMonitor activityMonitor;
    private final Handler mainHandler;
    private final InAppMessageDriver driver;
    private final AutomationEngine<InAppMessageSchedule> automationEngine;

    private final Runnable postDisplayRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentScheduleId != null) {
                return;
            }

            isDisplayedLocked = false;

            if (!carryOverScheduleIds.isEmpty()) {
                Activity activity = getResumedActivity();
                if (activity != null) {
                    display(activity, carryOverScheduleIds.pop(), true);
                }
            }


            automationEngine.checkPendingSchedules();
        }
    };


    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param configOptions The config options.
     * @param analytics Analytics instance.
     * @param activityMonitor The activity monitor.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public InAppMessageManager(Context context, AirshipConfigOptions configOptions, Analytics analytics, ActivityMonitor activityMonitor) {
        this.activityMonitor = activityMonitor;
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.driver = new InAppMessageDriver();
        this.driver.setAdapter(InAppMessage.TYPE_BANNER, new BannerDisplayAdapter());
        this.automationEngine = new AutomationEngine.Builder<InAppMessageSchedule>()
                .setAnalytics(analytics)
                .setActivityMonitor(activityMonitor)
                .setDataManager(new AutomationDataManager(context, configOptions.getAppKey(), "in-app"))
                .setScheduleLimit(200)
                .setDriver(driver)
                .build();


    }

    @VisibleForTesting
    InAppMessageManager(ActivityMonitor activityMonitor, InAppMessageDriver driver, AutomationEngine<InAppMessageSchedule> engine) {
        this.activityMonitor = activityMonitor;
        this.driver = driver;
        this.automationEngine = engine;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void init() {
        super.init();

        this.driver.setCallbacks(new InAppMessageDriver.Callbacks() {
            @Override
            public boolean isScheduleReadyToDisplay(InAppMessageSchedule schedule) {
                // If we have a current schedule ID or carry over schedule IDs we want
                // to finish displaying them before we display a new iam.
                if (currentScheduleId != null || !carryOverScheduleIds.isEmpty()) {
                    return false;
                }

                // Display may be locked to prevent back to back displays without a delay.
                if (isDisplayedLocked) {
                    return false;
                }

                // A resumed activity is required
                return getResumedActivity() != null;
            }

            @Override
            public void onDisplay(InAppMessageSchedule schedule, InAppMessageAdapter adapter, Bundle assets) {
                displayWrapperMap.put(schedule.getId(), new DisplayWrapper(schedule, adapter, assets));
                display(getResumedActivity(), schedule.getId(), false);
            }

            @Override
            public void onScheduleDataFetched(InAppMessageSchedule schedule) {
                automationEngine.checkPendingSchedules();
            }
        });

        Activity activity = activityMonitor.getResumedActivity();
        if (activity != null && !shouldIgnoreActivity(activity)) {
            resumedActivity = new WeakReference<>(activity);
        }

        activityMonitor.addListener(new ActivityMonitor.SimpleListener() {
            @Override
            public void onActivityStopped(Activity activity) {
                // Try to display any carry over schedule Ids if we do not have a
                // current schedule Id

                if (currentScheduleId != null && currentActivity != null && currentActivity.get() == activity && !activity.isChangingConfigurations()) {
                    currentScheduleId = null;
                    currentActivity = null;

                    Activity resumedActivity = getResumedActivity();
                    if (!carryOverScheduleIds.isEmpty() && resumedActivity != null) {
                        display(resumedActivity, carryOverScheduleIds.pop(), true);
                    } else {
                        mainHandler.postDelayed(postDisplayRunnable, MESSAGE_DISPLAY_INTERVAL_MS);
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                super.onActivityPaused(activity);
                resumedActivity = null;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (shouldIgnoreActivity(activity)) {
                    return;
                }

                resumedActivity = new WeakReference<>(activity);

                // Try to display any carry over schedule Ids if we do not have a
                // current schedule Id
                if (currentScheduleId == null && !carryOverScheduleIds.isEmpty()) {
                    display(activity, carryOverScheduleIds.pop(), true);
                }
            }
        });

        automationEngine.start();
    }

    /**
     * Schedules an in-app message.
     *
     * @param messageScheduleInfo The in-app message schedule info.
     * @return A pending result with the {@link InAppMessageSchedule}.
     */
    public PendingResult<InAppMessageSchedule> scheduleMessage(InAppMessageScheduleInfo messageScheduleInfo) {
        return automationEngine.schedule(messageScheduleInfo);
    }

    /**
     * Cancels an in-app message schedule.
     *
     * @param scheduleId The in-app message's schedule ID.
     * @return A pending result.
     */
    public PendingResult<Void> cancelSchedule(String scheduleId) {
        return automationEngine.cancel(Collections.singletonList(scheduleId));
    }

    /**
     * Cancels an in-app message schedule for the given message ID. If more than
     * one in-app message shares the same ID, they will all be cancelled.
     *
     * @param messageId The in-app message's ID.
     * @return A pending result.
     */
    public PendingResult<Void> cancelMessage(String messageId) {
        return automationEngine.cancelGroup(messageId);
    }

    /**
     * Sets a {@link InAppMessageAdapter} for a given message type.
     *
     * @param messageType The message type.
     * @param adapter The message adapter.
     */
    public void setAdapter(@InAppMessage.DisplayType String messageType, InAppMessageAdapter adapter) {
        driver.setAdapter(messageType, adapter);
    }

    /**
     * Called by the display handler when an in-app message is unable to finish displaying on the current activity
     * and needs to be redisplayed on the next.
     *
     * @param scheduleId The schedule ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void continueOnNextActivity(String scheduleId) {
        Logger.verbose("InAppMessagingManager - Continue message on next activity. ScheduleID: " + scheduleId);


        Activity previousActivity = currentActivity.get();

        // If it's equal to the current schedule ID clear and post a runnable to remove the display lock
        if (scheduleId.equals(currentScheduleId)) {
            currentScheduleId = null;
            currentActivity = null;
        }

        // Not in our record. Ignore it.
        if (!displayWrapperMap.containsKey(scheduleId)) {
            return;
        }

        Activity activity = getResumedActivity();
        if (currentScheduleId == null && activity != null && previousActivity != activity) {
            display(activity, scheduleId, true);
        } else if (!carryOverScheduleIds.contains(scheduleId)) {
            carryOverScheduleIds.push(scheduleId);
        }

        if (currentScheduleId == null) {
            mainHandler.postDelayed(postDisplayRunnable, MESSAGE_DISPLAY_INTERVAL_MS);
        }
    }


    /**
     * Called by the display handler when an in-app message is finished.
     *
     * @param scheduleId The schedule ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void messageFinished(String scheduleId) {
        Logger.verbose("InAppMessagingManager - Message finished. ScheduleID: " + scheduleId);

        // Duplicate call
        if (!displayWrapperMap.containsKey(scheduleId)) {
            return;
        }

        displayWrapperMap.remove(scheduleId);
        carryOverScheduleIds.remove(scheduleId);
        driver.displayFinished(scheduleId);

        // If it's equal to the current schedule ID clear it
        if (scheduleId.equals(currentScheduleId)) {
            currentScheduleId = null;
            currentActivity = null;
            mainHandler.postDelayed(postDisplayRunnable, MESSAGE_DISPLAY_INTERVAL_MS);
        }
    }

    /**
     * Called by the display handler to request the lock for the display to prevent multiple
     * in-app messages from displaying at the same time.
     *
     * @param activity The activity.
     * @param scheduleId The schedule ID.
     * @return {@code true} if the lock was granted or the in-app message already had the lock, otherwise
     * {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    boolean requestDisplayLock(Activity activity, String scheduleId) {
        Logger.verbose("InAppMessagingManager - Requesting display lock for schedule: " + scheduleId);

        if (scheduleId.equals(currentScheduleId)) {
            Logger.verbose("InAppMessagingManager - Schedule already obtained lock.");
            currentActivity = new WeakReference<>(activity);
            return true;
        }

        // If we do not have a record of the schedule return false. This could happen if the application
        // is suspended while displaying an iam and later restored. We will return false here and
        // let the automation engine trigger an execution for the iam.
        if (!displayWrapperMap.containsKey(scheduleId)) {
            Logger.error("InAppMessagingManager - Lock denied. No record of the schedule.");
            return false;
        }

        // Either set it as the current schedule ID or add it to the carry over schedule IDs
        if (currentScheduleId == null) {
            Logger.verbose("InAppMessagingManager - Lock granted");
            currentScheduleId = scheduleId;
            currentActivity = new WeakReference<>(activity);
            carryOverScheduleIds.remove(scheduleId);
            mainHandler.removeCallbacks(postDisplayRunnable);
            return true;
        }

        Logger.verbose("InAppMessagingManager - Lock denied. Another schedule is being displayed.");
        return false;
    }

    /**
     * Helper method to display the in-app message.
     *
     * @param activity The resumed activity.
     * @param scheduleId The schedule ID to display.
     * @param isRedisplay {@code true} if the schedule is being redisplayed, otherwise {@code false}.
     */
    @MainThread
    private void display(Activity activity, @NonNull String scheduleId, boolean isRedisplay) {
        if (!displayWrapperMap.containsKey(scheduleId)) {
            return;
        }

        if (activity != null && displayWrapperMap.get(scheduleId).display(activity, isRedisplay) == InAppMessageAdapter.OK) {
            Logger.verbose("InAppMessagingManager - Message displayed with scheduleId: " + scheduleId);
            this.currentScheduleId = scheduleId;
            this.isDisplayedLocked = true;
            this.currentActivity = new WeakReference<>(activity);
            mainHandler.removeCallbacks(postDisplayRunnable);
            carryOverScheduleIds.remove(scheduleId);
        } else if (!carryOverScheduleIds.contains(scheduleId)) {
            carryOverScheduleIds.push(scheduleId);
        }
    }

    /**
     * Get the resumed, in-app message allowed activity.
     *
     * @return The resumed activity.
     */
    private Activity getResumedActivity() {
        if (resumedActivity != null) {
            return resumedActivity.get();
        }

        return null;
    }

    /**
     * Helper method to check if the activity is marked as do not use.
     *
     * @param activity The activity.
     * @return {@code true} if the activity should be ignored for in-app messaging, otherwise {@code false}.
     */
    private boolean shouldIgnoreActivity(Activity activity) {
        ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
        if (info != null && info.metaData != null && info.metaData.getBoolean(EXCLUDE_FROM_AUTO_SHOW, false)) {
            Logger.verbose("InAppMessagingManager - Activity contains metadata to exclude it from auto showing an in-app message");
            return true;
        }

        return false;
    }

    /**
     * Helper class that wraps all the info it needs to display the in-app message in an adapter.
     */
    private static class DisplayWrapper {
        private final InAppMessageSchedule schedule;
        private final InAppMessageAdapter adapter;
        private final Bundle assets;

        DisplayWrapper(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessageAdapter adapter, @NonNull Bundle assets) {
            this.schedule = schedule;
            this.adapter = adapter;
            this.assets = assets;
        }

        /**
         * Displays the in-app message
         */
        @InAppMessageAdapter.AdapterResult
        int display(Activity activity, boolean isRedisplay) {
            try {
                DisplayArguments arguments = new DisplayArguments(schedule.getInfo().getInAppMessage(), assets, isRedisplay, new DisplayHandler(schedule.getId()));
                return adapter.display(activity, arguments);
            } catch (RuntimeException e) {
                Logger.error("Failed to display in-app message: ", e);
                return InAppMessageAdapter.RETRY;
            }
        }
    }
}
