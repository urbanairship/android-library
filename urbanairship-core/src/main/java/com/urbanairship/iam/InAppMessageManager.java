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
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

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
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.AlarmOperationScheduler;
import com.urbanairship.iam.banner.BannerAdapterFactory;
import com.urbanairship.iam.html.HtmlAdapterFactory;
import com.urbanairship.iam.modal.ModalAdapterFactory;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.iam.tags.TagGroupResult;
import com.urbanairship.json.JsonList;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.TagGroupRegistrar;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.iam.fullscreen.FullScreenAdapterFactory;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.RetryingExecutor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * In-app messaging manager.
 */
public class InAppMessageManager extends AirshipComponent implements InAppMessageScheduler {

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

    /**
     * Preference key for pausing/unpausing the in-app message manager.
     */
    private final static String PAUSE_KEY = "com.urbanairship.iam.paused";

    // State
    private String currentScheduleId;
    private WeakReference<Activity> resumedActivity;
    private WeakReference<Activity> currentActivity;
    private Stack<String> carryOverScheduleIds = new Stack<>();
    private Map<String, AdapterWrapper> adapterWrappers = new HashMap<>();
    private boolean isDisplayedLocked = false;
    private final InAppRemoteDataObserver remoteDataSubscriber;

    private final RetryingExecutor executor;
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
    private final TagGroupManager tagGroupManager;

    @Nullable
    private InAppMessageExtender messageExtender;

    private final Runnable postDisplayRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentScheduleId != null) {
                return;
            }

            isDisplayedLocked = false;

            if (!carryOverScheduleIds.isEmpty()) {
                String scheduleId = carryOverScheduleIds.peek();
                if (isDisplayReady(scheduleId)) {
                    display(getResumedActivity(), scheduleId);
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
    public InAppMessageManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore, @NonNull AirshipConfigOptions configOptions,
                               @NonNull Analytics analytics, @NonNull ActivityMonitor activityMonitor, @NonNull RemoteData remoteData,
                               @NonNull PushManager pushManager, @NonNull TagGroupRegistrar tagGroupRegistrar) {
        super(preferenceDataStore);

        this.activityMonitor = activityMonitor;
        this.remoteData = remoteData;
        this.analytics = analytics;
        this.pushManager = pushManager;
        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = new RetryingExecutor(this.mainHandler, Executors.newSingleThreadExecutor());
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

        this.tagGroupManager = new TagGroupManager(configOptions, pushManager, tagGroupRegistrar, preferenceDataStore);

        setAdapterFactory(InAppMessage.TYPE_BANNER, new BannerAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_FULLSCREEN, new FullScreenAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_MODAL, new ModalAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_HTML, new HtmlAdapterFactory());

    }

    @VisibleForTesting
    InAppMessageManager(PreferenceDataStore preferenceDataStore, Analytics analytics, ActivityMonitor activityMonitor,
                        RetryingExecutor executor, InAppMessageDriver driver, AutomationEngine<InAppMessageSchedule> engine,
                        RemoteData remoteData, PushManager pushManager, ActionRunRequestFactory actionRunRequestFactory,
                        TagGroupManager tagGroupManager) {
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
        this.tagGroupManager = tagGroupManager;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void init() {
        super.init();
        executor.setPaused(true);

        this.automationEngine.setScheduleExpiryListener(new AutomationEngine.ScheduleExpiryListener<InAppMessageSchedule>() {
            @Override
            public void onScheduleExpired(InAppMessageSchedule schedule) {
                analytics.addEvent(ResolutionEvent.messageExpired(schedule.getInfo().getInAppMessage(), schedule.getInfo().getEnd()));
            }
        });

        driver.setListener(new InAppMessageDriver.Listener() {
            @Override
            @WorkerThread
            public void onPrepareMessage(@NonNull String scheduleId, @NonNull InAppMessage message) {
                prepareMessage(scheduleId, message);
            }

            @Override
            @MainThread
            public boolean isMessageReady(@NonNull String scheduleId, @NonNull InAppMessage message) {
                return isDisplayReady(scheduleId);
            }

            @Override
            @MainThread
            public void onDisplay(@NonNull String scheduleId) {
                display(getResumedActivity(), scheduleId);
            }
        });

        tagGroupManager.setRequestTagsCallback(new TagGroupManager.RequestTagsCallback() {
            @Override
            public Map<String, Set<String>> getTags() throws ExecutionException, InterruptedException {
                Map<String, Set<String>> tags = new HashMap<>();

                for (InAppMessageSchedule schedule : getSchedules().get()) {
                    Audience audience = schedule.getInfo().getInAppMessage().getAudience();
                    if (audience == null || audience.getTagSelector() == null || !audience.getTagSelector().containsTagGroups()) {
                        continue;
                    }

                    tags.putAll(audience.getTagSelector().getTagGroups());
                }

                return tags;
            }
        });

        automationEngine.start();
        automationEngine.setPaused(true);
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
        executor.setPaused(false);
        remoteDataSubscriber.subscribe(remoteData, this);
    }

    @Override
    protected void tearDown() {
        super.tearDown();
        remoteDataSubscriber.cancel();
        automationEngine.stop();
    }

    @Override
    public void onNewConfig(@NonNull JsonList configList) {
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonList(configList);
        if (config == null) {
            return;
        }

        if (config.tagGroupsConfig != null) {
            tagGroupManager.setEnabled(config.tagGroupsConfig.isEnabled);
            tagGroupManager.setCacheStaleReadTime(config.tagGroupsConfig.cacheStaleReadTimeSeconds, TimeUnit.SECONDS);
            tagGroupManager.setPreferLocalTagDataTime(config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds, TimeUnit.SECONDS);
            tagGroupManager.setCacheMaxAgeTime(config.tagGroupsConfig.cacheMaxAgeInSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void onComponentEnableChange(boolean isEnabled) {
        updateEnginePauseState();
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
    public PendingResult<Collection<InAppMessageSchedule>> getSchedules() {
        return automationEngine.getSchedules();
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
     * Sets the message extender. The message will be extended after its been
     * triggered, but before the adapter is created.
     *
     * @param extender The extender.
     */
    public void setMessageExtender(@Nullable InAppMessageExtender extender) {
        this.messageExtender = extender;
    }

    /**
     * Pauses or unpauses in-app messaging.
     *
     * @param paused {@code true} to pause in-app message display, otherwise {@code false}.
     */
    public void setPaused(boolean paused) {
        boolean storedPausedState = getDataStore().getBoolean(PAUSE_KEY, paused);

        // Only update when paused state transitions from paused to unpaused
        if (storedPausedState == true && storedPausedState != paused) {
            automationEngine.checkPendingSchedules();
        }

        getDataStore().put(PAUSE_KEY, paused);
    }

    /**
     * Returns {@code true} if in-app message display is paused, otherwise {@code false}.
     *
     * @return {@code true} if in-app message display is paused, otherwise {@code false}.
     */
    public boolean isPaused() {
        return getDataStore().getBoolean(PAUSE_KEY, false);
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

    private boolean isDisplayReady(@NonNull String scheduleId) {
        // If we have a current schedule ID, do not display the next schedule.
        if (currentScheduleId != null) {
            return false;
        }

        // If its not a carry over schedule ID and we have pending schedules return false.
        if (!carryOverScheduleIds.isEmpty() && !carryOverScheduleIds.contains(scheduleId)) {
            return false;
        }

        // Display may be locked to prevent back to back displays without a delay.
        if (isDisplayedLocked) {
            return false;
        }

        // Prevent display on pause.
        if (isPaused()) {
            return false;
        }

        // Make sure we have a current activity
        Activity currentActivity = getResumedActivity();
        if (currentActivity == null) {
            return false;
        }

        AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        return adapterWrapper != null && adapterWrapper.isReady(currentActivity);
    }

    /**
     * Prepares a message to be displayed.
     * @param scheduleId The schedule ID.
     * @param message The message.
     */
    private void prepareMessage(final String scheduleId, final InAppMessage message) {

        // Create the adapter
        final RetryingExecutor.Operation createAdapter = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                // Create the adapter with the extended message
                AdapterWrapper adapter = createAdapter(scheduleId, extendMessage(message));

                // Skip if we were unable to create an adapter
                if (adapter == null) {
                    driver.messagePrepared(scheduleId, AutomationDriver.RESULT_SKIP_PENALIZE);
                    return RetryingExecutor.RESULT_CANCEL;
                }

                adapterWrappers.put(scheduleId, adapter);
                return RetryingExecutor.RESULT_FINISHED;
            }
        };

        // Audience checks
        RetryingExecutor.Operation checkAudience = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                AdapterWrapper adapter = adapterWrappers.get(scheduleId);
                if (adapter == null) {
                    return RetryingExecutor.RESULT_CANCEL;
                }

                InAppMessage message = adapter.message;
                if (message.getAudience() == null) {
                    return RetryingExecutor.RESULT_FINISHED;
                }

                Map<String, Set<String>> tagGroups = null;

                if (message.getAudience().getTagSelector() != null && message.getAudience().getTagSelector().containsTagGroups()) {
                    Map<String, Set<String>> tags = message.getAudience().getTagSelector().getTagGroups();
                    TagGroupResult result = tagGroupManager.getTags(tags);
                    if (!result.success) {
                         return RetryingExecutor.RESULT_RETRY;
                    }

                    tagGroups = result.tagGroups;
                }

                if (AudienceChecks.checkAudience(UAirship.getApplicationContext(), message.getAudience(), tagGroups)) {
                    return RetryingExecutor.RESULT_FINISHED;
                }

                driver.messagePrepared(scheduleId, AutomationDriver.RESULT_SKIP_PENALIZE);
                return RetryingExecutor.RESULT_CANCEL;
            }
        };

        // Prepare Adapter
        RetryingExecutor.Operation prepareAdapter = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                AdapterWrapper adapter = adapterWrappers.get(scheduleId);
                if (adapter == null) {
                    return RetryingExecutor.RESULT_CANCEL;
                }

                @InAppMessageAdapter.PrepareResult
                int result = adapter.prepare();

                switch (result) {
                    case InAppMessageAdapter.OK:
                        Logger.debug("InAppMessageManager - Scheduled message prepared for display: " + scheduleId);

                        // Store the adapter
                        adapterWrappers.put(scheduleId, adapter);
                        driver.messagePrepared(scheduleId, AutomationDriver.RESULT_CONTINUE);
                        return RetryingExecutor.RESULT_FINISHED;

                    case InAppMessageAdapter.RETRY:
                        Logger.debug("InAppMessageManager - Scheduled message failed to prepare for display: " + scheduleId);
                        return RetryingExecutor.RESULT_RETRY;

                    case InAppMessageAdapter.CANCEL:
                    default:
                        driver.messagePrepared(scheduleId, AutomationDriver.RESULT_CANCEL_SCHEDULE);
                        return RetryingExecutor.RESULT_CANCEL;
                }
            }
        };

        // Execute the operations
        executor.execute(createAdapter, checkAudience, prepareAdapter);
    }

    /**
     * Creates an adapter.
     * @param scheduleId The schedule ID.
     * @param message The message.
     * @return The adapter.
     */
    @Nullable
    private AdapterWrapper createAdapter(String scheduleId, InAppMessage message) {
        InAppMessageAdapter adapter = null;

        try {
            InAppMessageAdapter.Factory factory;
            synchronized (adapterFactories) {
                factory = adapterFactories.get(message.getType());
            }

            if (factory == null) {
                Logger.debug("InAppMessageManager - No display adapter for message type: " + message.getType() + ". Unable to process schedule: " + scheduleId);
            } else {
                adapter = factory.createAdapter(message);
            }
        } catch (Exception e) {
            Logger.error("InAppMessageManager - Failed to create in-app message adapter.", e);
        }

        if (adapter == null) {
            Logger.error("InAppMessageManager - Failed to create in-app message adapter.");
            return null;
        }

        return new AdapterWrapper(scheduleId, message, adapter);
    }

    /**
     * Extends the in-app message.
     * @param originalMessage The original message.
     * @return The extended message, or the original message if no extender is set.
     */
    private InAppMessage extendMessage(InAppMessage originalMessage) {
        // Extend the message
        InAppMessageExtender extender = InAppMessageManager.this.messageExtender;
        if (extender != null) {
            return extender.extend(originalMessage);
        }

        return originalMessage;
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
        if (!isPaused() && currentScheduleId == null && activity != null && previousActivity != activity) {
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

        if (resolutionInfo.getButtonInfo() != null && ButtonInfo.BEHAVIOR_CANCEL.equals(resolutionInfo.getButtonInfo().getBehavior())) {
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

        if (adapterWrapper == null) {
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

}
