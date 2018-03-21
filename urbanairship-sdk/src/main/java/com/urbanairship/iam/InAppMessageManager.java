/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntRange;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDataManager;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.AlarmOperationScheduler;
import com.urbanairship.iam.banner.BannerAdapterFactory;
import com.urbanairship.iam.html.HtmlAdapterFactory;
import com.urbanairship.iam.modal.ModalAdapterFactory;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.iam.fullscreen.FullScreenAdapterFactory;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * In-app messaging manager.
 */
public class InAppMessageManager extends AirshipComponent implements InAppMessageScheduler {

    /**
     * Prepare retry delay.
     */
    private static final long PREPARE_RETRY_DELAY_MS = 30000;

    /**
     * Display retry delay.
     */
    private static final long RETRY_DISPLAY_DELAY_MS = 30000;

    /**
     * Default delay between displaying in-app messages.
     */
    public static final long DEFAULT_DISPLAY_INTERVAL_MS = 30000;

    /**
     * Metadata an app can use to prevent an in-app message from showing on a specific activity.
     */
    public final static String EXCLUDE_FROM_AUTO_SHOW = "com.urbanairship.push.iam.EXCLUDE_FROM_AUTO_SHOW";

    /**
     * Preference key for enabling/disabling the in-app message manager.
     */
    private final static String ENABLE_KEY = "com.urbanairship.iam.enabled";

    // State
    private String currentScheduleId;
    private WeakReference<Activity> resumedActivity;
    private WeakReference<Activity> currentActivity;
    private Stack<String> carryOverScheduleIds = new Stack<>();
    private Map<String, AdapterWrapper> adapterWrappers = new HashMap<>();
    private boolean isDisplayedLocked = false;
    private boolean isAirshipReady = false;
    private final InAppRemoteDataObserver remoteDataSubscriber;

    private final Executor executor;
    private final ActionRunRequestFactory actionRunRequestFactory;
    private final ActivityMonitor activityMonitor;
    private final RemoteData remoteData;
    private final Analytics analytics;
    private final PushManager pushManager;
    private final Handler mainHandler;
    private final InAppMessageDriver driver;
    private final AutomationEngine<InAppMessageSchedule> automationEngine;
    private final Map<String, InAppMessageAdapter.Factory> adapterFactories = new HashMap<>();
    private long displayInterval = DEFAULT_DISPLAY_INTERVAL_MS;
    private final List<InAppMessageListener> listeners = new ArrayList<>();


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
                    display(activity, carryOverScheduleIds.pop());
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
     * @param remoteData Remote data.
     * @param pushManager The push manager.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public InAppMessageManager(Context context, PreferenceDataStore preferenceDataStore, AirshipConfigOptions configOptions,
                               Analytics analytics, ActivityMonitor activityMonitor, RemoteData remoteData, PushManager pushManager) {
        super(preferenceDataStore);

        this.activityMonitor = activityMonitor;
        this.remoteData = remoteData;
        this.analytics = analytics;
        this.pushManager = pushManager;
        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.driver = new InAppMessageDriver();
        this.automationEngine = new AutomationEngine.Builder<InAppMessageSchedule>()
                .setAnalytics(analytics)
                .setActivityMonitor(activityMonitor)
                .setDataManager(new AutomationDataManager(context, configOptions.getAppKey(), "in-app"))
                .setScheduleLimit(200)
                .setDriver(driver)
                .setOperationScheduler(AlarmOperationScheduler.shared(context))
                .build();
        this.actionRunRequestFactory = new ActionRunRequestFactory();

        setAdapterFactory(InAppMessage.TYPE_BANNER, new BannerAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_FULLSCREEN, new FullScreenAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_MODAL, new ModalAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_HTML, new HtmlAdapterFactory());

    }

    @VisibleForTesting
    InAppMessageManager(PreferenceDataStore preferenceDataStore, Analytics analytics, ActivityMonitor activityMonitor,
                        Executor executor, InAppMessageDriver driver, AutomationEngine<InAppMessageSchedule> engine,
                        RemoteData remoteData, PushManager pushManager, ActionRunRequestFactory actionRunRequestFactory) {
        super(preferenceDataStore);
        this.analytics = analytics;
        this.activityMonitor = activityMonitor;
        this.remoteData = remoteData;
        this.pushManager = pushManager;
        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore);
        this.driver = driver;
        this.automationEngine = engine;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = executor;
        this.actionRunRequestFactory = actionRunRequestFactory;
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
            public boolean isMessageReady(@NonNull String scheduleId, @NonNull InAppMessage message) {
                if (!isAirshipReady) {
                    return false;
                }

                AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
                if (adapterWrapper == null) {

                    if (!AudienceChecks.checkAudience(UAirship.getApplicationContext(), message.getAudience())) {
                        Logger.debug("InAppMessageManager - Message audience conditions not met, skipping schedule: " + scheduleId);
                        return true;
                    }

                    InAppMessageAdapter.Factory factory = adapterFactories.get(message.getType());
                    if (factory == null) {
                        Logger.debug("InAppMessageManager - No display adapter for message type: " + message.getType() + ". Unable to process schedule: " + scheduleId);
                        cancelSchedule(scheduleId);
                        return false;
                    }

                    try {
                        InAppMessageAdapter adapter = factory.createAdapter(message);
                        if (adapter == null) {
                            Logger.error("InAppMessageManager - Failed to create in-app message adapter.");
                            cancelSchedule(scheduleId);
                            return false;
                        }
                        adapterWrappers.put(scheduleId, new AdapterWrapper(scheduleId, message, adapter));
                    } catch (Exception e) {
                        Logger.error("InAppMessageManager - Failed to create in-app message adapter.", e);
                        cancelSchedule(scheduleId);
                        return false;
                    }

                    prepareMessage(scheduleId);
                    return false;
                }

                if (adapterWrapper.skipDisplay) {
                    return true;
                }

                if (!adapterWrapper.isReady) {
                    return false;
                }

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
            public void onDisplay(@NonNull String scheduleId) {
                display(getResumedActivity(), scheduleId);
            }
        });

        this.automationEngine.setScheduleExpiryListener(new AutomationEngine.ScheduleExpiryListener<InAppMessageSchedule>() {
            @Override
            public void onScheduleExpired(InAppMessageSchedule schedule) {
                analytics.addEvent(ResolutionEvent.messageExpired(schedule.getInfo().getInAppMessage(), schedule.getInfo().getEnd()));
            }
        });


        automationEngine.start();
        updateEnginePauseState();

        // New user cut off time
        if (remoteDataSubscriber.getScheduleNewUserCutOffTime() == -1) {
            remoteDataSubscriber.setScheduleNewUserCutOffTime(pushManager.getChannelId() == null ? System.currentTimeMillis() : 0);
        }

        // Finish init on the main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                finishInit();
            }
        });
    }

    /**
     * Called during {@link #init()} to finish any initialization
     * that needs to happen on the main thread.
     */
    @MainThread
    private void finishInit() {
        // Get the current resumed activity
        Activity activity = activityMonitor.getResumedActivity();
        if (activity != null && !shouldIgnoreActivity(activity)) {
            resumedActivity = new WeakReference<>(activity);
        }

        // Add the activity listener
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
                        display(resumedActivity, carryOverScheduleIds.pop());
                    } else {
                        mainHandler.postDelayed(postDisplayRunnable, displayInterval);
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
                    display(activity, carryOverScheduleIds.pop());
                }

                automationEngine.checkPendingSchedules();
            }
        });

        // If we are already in the foreground check pending schedules.
        if (activityMonitor.isAppForegrounded()) {
            automationEngine.checkPendingSchedules();
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onAirshipReady(UAirship airship) {
        super.onAirshipReady(airship);
        isAirshipReady = true;
        automationEngine.checkPendingSchedules();
        remoteDataSubscriber.subscribe(remoteData, this);
    }

    @Override
    protected void tearDown() {
        super.tearDown();
        remoteDataSubscriber.cancel();
        automationEngine.stop();
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void onComponentEnableChange(boolean isEnabled) {
        updateEnginePauseState();
    }

    private void prepareMessage(final String scheduleId) {
        if (!adapterWrappers.containsKey(scheduleId)) {
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
                if (adapterWrapper == null) {
                    return;
                }

                @InAppMessageAdapter.PrepareResult
                int result = adapterWrapper.prepare();

                switch (result) {
                    case InAppMessageAdapter.OK:
                        Logger.debug("InAppMessageManager - Scheduled message prepared for display: " + scheduleId);
                        automationEngine.checkPendingSchedules();
                        break;

                    case InAppMessageAdapter.RETRY:
                        Logger.debug("InAppMessageManager - Scheduled message failed to prepare for display: " + scheduleId);
                        // Retry after a delay
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                prepareMessage(scheduleId);
                            }
                        }, PREPARE_RETRY_DELAY_MS);
                        break;
                    case InAppMessageAdapter.CANCEL:
                        cancelSchedule(scheduleId);
                        adapterWrappers.remove(scheduleId);
                        break;
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos) {
        return automationEngine.schedule(scheduleInfos);
    }

    /**
     * {@inheritDoc}
     */
    public PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo) {
        return automationEngine.schedule(messageScheduleInfo);
    }

    /**
     * {@inheritDoc}
     */
    public PendingResult<Void> cancelSchedule(@NonNull String scheduleId) {
        return automationEngine.cancel(Collections.singletonList(scheduleId));
    }

    /**
     * {@inheritDoc}
     */
    public PendingResult<Boolean> cancelMessage(@NonNull String messageId) {
        return automationEngine.cancelGroup(messageId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<Void> cancelMessages(@NonNull Collection<String> messageIds) {
        return automationEngine.cancelGroups(messageIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<Collection<InAppMessageSchedule>> getSchedules(@NonNull final String messageId) {
        return automationEngine.getSchedules(messageId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<InAppMessageSchedule> getSchedule(@NonNull String scheduleId) {
        return automationEngine.getSchedule(scheduleId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<InAppMessageSchedule> editSchedule(@NonNull String scheduleId, @NonNull InAppMessageScheduleEdits edit) {
        return automationEngine.editSchedule(scheduleId, edit);
    }

    /**
     * Sets a {@link InAppMessageAdapter} for a given display type.
     *
     * @param displayType The display type.
     * @param factory The adapter factory.
     */
    public void setAdapterFactory(@InAppMessage.DisplayType String displayType, InAppMessageAdapter.Factory factory) {
        if (factory == null) {
            adapterFactories.remove(displayType);
        } else {
            adapterFactories.put(displayType, factory);
        }
    }

    /**
     * Sets the in-app message display interval. Defaults to {@link #DEFAULT_DISPLAY_INTERVAL_MS}.
     *
     * @param time The display interval.
     * @param timeUnit The time unit.
     */
    public void setDisplayInterval(@IntRange(from = 0) long time, @NonNull TimeUnit timeUnit) {
        this.displayInterval = timeUnit.toMillis(time);
    }

    /**
     * Gets the display interval in milliseconds.
     *
     * @return The display interval in milliseconds.
     */
    public long getDisplayInterval() {
        return this.displayInterval;
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    public void addListener(@NonNull InAppMessageListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     */
    public void removeListener(@NonNull InAppMessageListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Enables or disables in-app messaging.
     *
     * @param enabled {@code true} to enable in-app messaging, otherwise {@code false}.
     */
    public void setEnabled(boolean enabled) {
        getDataStore().put(ENABLE_KEY, enabled);
        updateEnginePauseState();
    }

    /**
     * Returns {@code true} if in-app messaging is enabled, {@code false} if its disabled.
     *
     * @return {@code true} if in-app messaging is enabled, {@code false} if its disabled.
     */
    public boolean isEnabled() {
        return getDataStore().getBoolean(ENABLE_KEY, true);
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
        if (!adapterWrappers.containsKey(scheduleId)) {
            return;
        }

        Activity activity = getResumedActivity();
        if (currentScheduleId == null && activity != null && previousActivity != activity) {
            display(activity, scheduleId);
        } else if (!carryOverScheduleIds.contains(scheduleId)) {
            carryOverScheduleIds.push(scheduleId);
        }

        if (currentScheduleId == null) {
            if (displayInterval > 0) {
                mainHandler.postDelayed(postDisplayRunnable, displayInterval);
            } else {
                mainHandler.post(postDisplayRunnable);
            }
        }
    }


    /**
     * Called by the display handler when an in-app message is finished.
     *
     * @param scheduleId The schedule ID.
     * @param resolutionInfo Info on why the event is finished.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void messageFinished(@NonNull String scheduleId, @NonNull ResolutionInfo resolutionInfo) {
        Logger.verbose("InAppMessagingManager - Message finished. ScheduleID: " + scheduleId);

        final AdapterWrapper adapterWrapper = adapterWrappers.remove(scheduleId);

        // No record
        if (adapterWrapper == null) {
            return;
        }

        // Add resolution event
        analytics.addEvent(ResolutionEvent.messageResolution(adapterWrapper.message, resolutionInfo));

        // Run Actions
        InAppActionUtils.runActions(adapterWrapper.message.getActions(), actionRunRequestFactory);

        synchronized (listeners) {
            for (InAppMessageListener listener : new ArrayList<>(listeners)) {
                listener.onMessageFinished(scheduleId, adapterWrapper.message, resolutionInfo);
            }
        }

        driver.displayFinished(scheduleId);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                adapterWrapper.finish();
            }
        });

        carryOverScheduleIds.remove(scheduleId);

        // If it's equal to the current schedule ID clear it
        if (scheduleId.equals(currentScheduleId)) {
            currentScheduleId = null;
            currentActivity = null;

            if (displayInterval > 0) {
                mainHandler.postDelayed(postDisplayRunnable, displayInterval);
            } else {
                mainHandler.post(postDisplayRunnable);
            }
        }

        if (resolutionInfo.buttonInfo != null && ButtonInfo.BEHAVIOR_CANCEL.equals(resolutionInfo.buttonInfo.getBehavior())) {
            cancelSchedule(scheduleId);
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
        if (!adapterWrappers.containsKey(scheduleId)) {
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
     */
    @MainThread
    private void display(Activity activity, @NonNull String scheduleId) {
        final AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);

        if (adapterWrapper == null || adapterWrapper.skipDisplay) {
            if (adapterWrapper != null) {
                adapterWrappers.remove(scheduleId);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        adapterWrapper.finish();
                    }
                });
            }

            driver.displayFinished(scheduleId);
            return;
        }

        carryOverScheduleIds.remove(scheduleId);
        mainHandler.removeCallbacks(postDisplayRunnable);

        boolean isRedisplay = adapterWrapper.displayed;

        if (activity != null && adapterWrapper.display(activity)) {
            Logger.verbose("InAppMessagingManager - Message displayed with scheduleId: " + scheduleId);
            this.currentScheduleId = scheduleId;
            this.isDisplayedLocked = true;
            this.currentActivity = new WeakReference<>(activity);

            if (!isRedisplay) {
                analytics.addEvent(new DisplayEvent(adapterWrapper.message));

                synchronized (listeners) {
                    for (InAppMessageListener listener : new ArrayList<>(listeners)) {
                        listener.onMessageDisplayed(scheduleId, adapterWrapper.message);
                    }
                }
            }
        } else {
            carryOverScheduleIds.push(scheduleId);
            mainHandler.postDelayed(postDisplayRunnable, RETRY_DISPLAY_DELAY_MS);
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
     * Updates the automation engine pause state with user enable and component enable flags.
     */
    private void updateEnginePauseState() {
        automationEngine.setPaused(!(isEnabled() && isComponentEnabled()));
    }

    /**
     * Helper class that keeps track of the schedule's adapter, assets, and execution callback.
     */
    public static final class AdapterWrapper {
        private final String scheduleId;
        private final InAppMessage message;
        public volatile boolean isReady;
        public volatile boolean skipDisplay;
        public InAppMessageAdapter adapter;
        private boolean displayed = false;
        private boolean prepareCalled = false;

        public AdapterWrapper(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull InAppMessageAdapter adapter) {
            this.scheduleId = scheduleId;
            this.message = message;
            this.adapter = adapter;
        }

        @InAppMessageAdapter.PrepareResult
        private int prepare() {

            if (!AudienceChecks.checkAudience(UAirship.getApplicationContext(), message.getAudience())) {
                skipDisplay = true;
                Logger.debug("InAppMessageManager - Message audience conditions not met, skipping schedule: " + scheduleId);
                return InAppMessageAdapter.OK;
            }

            try {
                Logger.debug("InAppMessageManager - Preparing schedule: " + scheduleId);

                @InAppMessageAdapter.PrepareResult
                int result = adapter.onPrepare(UAirship.getApplicationContext());
                prepareCalled = true;

                if (result == InAppMessageAdapter.OK) {
                    isReady = true;
                }
                return result;
            } catch (Exception e) {
                Logger.error("InAppMessageManager - Failed to prepare in-app message.", e);
                return InAppMessageAdapter.RETRY;
            }
        }

        private boolean display(Activity activity) {
            Logger.debug("InAppMessageManager - Displaying schedule: " + scheduleId);
            try {
                DisplayHandler displayHandler = new DisplayHandler(scheduleId);
                if (adapter.onDisplay(activity, displayed, displayHandler)) {
                    displayed = true;
                    return true;
                }

                return false;
            } catch (Exception e) {
                Logger.error("InAppMessageManager - Failed to display in-app message.", e);
                return false;
            }
        }

        private void finish() {
            Logger.debug("InAppMessageManager - Schedule finished: " + scheduleId);

            try {
                if (prepareCalled) {
                    adapter.onFinish();
                }
            } catch (Exception e) {
                Logger.error("InAppMessageManager - Exception during onFinish().", e);
            }
        }
    }

}
