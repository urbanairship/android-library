/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntRange;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.AlarmOperationScheduler;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.automation.AutomationDataManager;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.iam.banner.BannerAdapterFactory;
import com.urbanairship.iam.fullscreen.FullScreenAdapterFactory;
import com.urbanairship.iam.html.HtmlAdapterFactory;
import com.urbanairship.iam.modal.ModalAdapterFactory;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.iam.tags.TagGroupResult;
import com.urbanairship.iam.tags.TagGroupUtils;
import com.urbanairship.json.JsonList;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.TagGroupRegistrar;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.RetryingExecutor;

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
     * Default delay between displaying in-app messages.
     */
    public static final long DEFAULT_DISPLAY_INTERVAL_MS = 30000;

    /**
     * Preference key for enabling/disabling the in-app message manager.
     */
    private final static String ENABLE_KEY = "com.urbanairship.iam.enabled";

    /**
     * Preference key for pausing/unpausing the in-app message manager.
     */
    private final static String PAUSE_KEY = "com.urbanairship.iam.paused";

    // State
    private final Map<String, AdapterWrapper> adapterWrappers = new HashMap<>();
    private final InAppRemoteDataObserver remoteDataSubscriber;

    private final RetryingExecutor executor;
    private final ActionRunRequestFactory actionRunRequestFactory;
    private final RemoteData remoteData;
    private final Analytics analytics;
    private final PushManager pushManager;
    private final Handler mainHandler;
    private final InAppMessageDriver driver;

    private final AutomationEngine<InAppMessageSchedule> automationEngine;
    private final Map<String, InAppMessageAdapter.Factory> adapterFactories = new HashMap<>();
    private final List<InAppMessageListener> listeners = new ArrayList<>();
    private final TagGroupManager tagGroupManager;
    private final DefaultDisplayCoordinator defaultCoordinator;

    @Nullable
    private InAppMessageExtender messageExtender;

    @Nullable
    private OnRequestDisplayCoordinatorCallback displayCoordinatorCallback;

    private final DisplayCoordinator.OnDisplayReadyCallback displayReadyCallback = new DisplayCoordinator.OnDisplayReadyCallback() {
        @Override
        public void onReady() {
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
                               @NonNull Analytics analytics, @NonNull RemoteData remoteData, ActivityMonitor activityMonitor,
                               @NonNull PushManager pushManager, @NonNull TagGroupRegistrar tagGroupRegistrar) {
        super(context, preferenceDataStore);

        this.defaultCoordinator = new DefaultDisplayCoordinator(activityMonitor);
        this.remoteData = remoteData;
        this.analytics = analytics;
        this.pushManager = pushManager;
        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = new RetryingExecutor(this.mainHandler, AirshipExecutors.newSerialExecutor());
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
    InAppMessageManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore, Analytics analytics, ActivityMonitor activityMonitor,
                        RetryingExecutor executor, InAppMessageDriver driver, AutomationEngine<InAppMessageSchedule> engine,
                        RemoteData remoteData, PushManager pushManager, ActionRunRequestFactory actionRunRequestFactory,
                        TagGroupManager tagGroupManager) {
        super(context, preferenceDataStore);

        this.defaultCoordinator = new DefaultDisplayCoordinator(activityMonitor);
        this.analytics = analytics;
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
            public void onScheduleExpired(@NonNull InAppMessageSchedule schedule) {
                analytics.addEvent(ResolutionEvent.messageExpired(schedule.getInfo().getInAppMessage(), schedule.getInfo().getEnd()));
            }
        });

        driver.setListener(new InAppMessageDriver.Listener() {
            @Override
            public void onPrepareSchedule(@NonNull InAppMessageSchedule schedule) {
                InAppMessageManager.this.prepareSchedule(schedule);
            }

            @Override
            public boolean isScheduleReady(@NonNull InAppMessageSchedule schedule) {
                return InAppMessageManager.this.isScheduleReady(schedule.getId());
            }

            @Override
            public void onExecuteSchedule(@NonNull InAppMessageSchedule schedule) {
                InAppMessageManager.this.executeSchedule(schedule.getId());
            }
        });

        tagGroupManager.setRequestTagsCallback(new TagGroupManager.RequestTagsCallback() {
            @NonNull
            @Override
            public Map<String, Set<String>> getTags() throws ExecutionException, InterruptedException {
                Map<String, Set<String>> tags = new HashMap<>();

                Collection<InAppMessageSchedule> schedules = getSchedules().get();
                if (schedules == null) {
                    return tags;
                }

                for (InAppMessageSchedule schedule : schedules) {
                    Audience audience = schedule.getInfo().getInAppMessage().getAudience();
                    if (audience != null && audience.getTagSelector() != null && audience.getTagSelector().containsTagGroups()) {
                        TagGroupUtils.addAll(tags, audience.getTagSelector().getTagGroups());
                    }
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
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
        executor.setPaused(false);
        remoteDataSubscriber.subscribe(remoteData, this);
        automationEngine.checkPendingSchedules();
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
    @NonNull
    @Override
    public PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos) {
        return automationEngine.schedule(scheduleInfos);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo) {
        return automationEngine.schedule(messageScheduleInfo);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Void> cancelSchedule(@NonNull String scheduleId) {
        return automationEngine.cancel(Collections.singletonList(scheduleId));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelMessage(@NonNull String messageId) {
        return automationEngine.cancelGroup(messageId);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Void> cancelMessages(@NonNull Collection<String> messageIds) {
        return automationEngine.cancelGroups(messageIds);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<InAppMessageSchedule>> getSchedules(@NonNull final String messageId) {
        return automationEngine.getSchedules(messageId);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<InAppMessageSchedule>> getSchedules() {
        return automationEngine.getSchedules();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<InAppMessageSchedule> getSchedule(@NonNull String scheduleId) {
        return automationEngine.getSchedule(scheduleId);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
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
    public void setAdapterFactory(@NonNull @InAppMessage.DisplayType String displayType, @Nullable InAppMessageAdapter.Factory factory) {
        if (factory == null) {
            adapterFactories.remove(displayType);
        } else {
            adapterFactories.put(displayType, factory);
        }
    }

    /**
     * Sets the in-app message display interval on the default display coordinator.
     * Defaults to {@link #DEFAULT_DISPLAY_INTERVAL_MS}.
     *
     * @param time The display interval.
     * @param timeUnit The time unit.
     */
    public void setDisplayInterval(@IntRange(from = 0) long time, @NonNull TimeUnit timeUnit) {
        this.defaultCoordinator.setDisplayInterval(time, timeUnit);
    }

    /**
     * Gets the display interval in milliseconds.
     *
     * @return The display interval in milliseconds.
     */
    public long getDisplayInterval() {
        return this.defaultCoordinator.getDisplayInterval();
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
        if (storedPausedState && storedPausedState != paused) {
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

    /**
     * Sets the callback for requesting display coordinators.
     *
     * @param callback The display request callback.
     */
    public void setOnRequestDisplayCoordinatorCallback(@Nullable OnRequestDisplayCoordinatorCallback callback) {
        this.displayCoordinatorCallback = callback;
    }


    /**
     * Prepares a schedule to be displayed.
     *
     * @param schedule The schedule.
     */
    private void prepareSchedule(@NonNull final InAppMessageSchedule schedule) {

        // Create the adapter
        final RetryingExecutor.Operation createAdapter = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                // Create the adapter with the extended message
                AdapterWrapper adapter = createAdapterWrapper(schedule);

                // Skip if we were unable to create an adapter
                if (adapter == null) {
                    driver.schedulePrepared(schedule.getId(), AutomationDriver.RESULT_PENALIZE);
                    return RetryingExecutor.RESULT_CANCEL;
                }

                adapterWrappers.put(schedule.getId(), adapter);
                return RetryingExecutor.RESULT_FINISHED;
            }
        };

        // Audience checks
        RetryingExecutor.Operation checkAudience = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                AdapterWrapper adapter = adapterWrappers.get(schedule.getId());
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

                @AutomationDriver.PrepareResult int result = AutomationDriver.RESULT_PENALIZE;
                switch (message.getAudience().getMissBehavior()) {
                    case Audience.MISS_BEHAVIOR_CANCEL:
                        result = AutomationDriver.RESULT_CANCEL;
                        break;
                    case Audience.MISS_BEHAVIOR_SKIP:
                        result = AutomationDriver.RESULT_SKIP;
                        break;
                    case Audience.MISS_BEHAVIOR_PENALIZE:
                        result = AutomationDriver.RESULT_PENALIZE;
                        break;
                }
                driver.schedulePrepared(schedule.getId(), result);
                return RetryingExecutor.RESULT_CANCEL;
            }
        };

        // Prepare Adapter
        RetryingExecutor.Operation prepareAdapter = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                AdapterWrapper adapter = adapterWrappers.get(schedule.getId());
                if (adapter == null) {
                    return RetryingExecutor.RESULT_CANCEL;
                }

                @InAppMessageAdapter.PrepareResult
                int result = adapter.prepare(getContext());

                switch (result) {
                    case InAppMessageAdapter.OK:
                        Logger.debug("InAppMessageManager - Scheduled message prepared for display: %s", schedule.getId());

                        // Store the adapter
                        adapterWrappers.put(schedule.getId(), adapter);
                        driver.schedulePrepared(schedule.getId(), AutomationDriver.RESULT_CONTINUE);
                        return RetryingExecutor.RESULT_FINISHED;

                    case InAppMessageAdapter.RETRY:
                        Logger.debug("InAppMessageManager - Scheduled message failed to prepare for display: %s", schedule.getId());
                        return RetryingExecutor.RESULT_RETRY;

                    case InAppMessageAdapter.CANCEL:
                    default:
                        driver.schedulePrepared(schedule.getId(), AutomationDriver.RESULT_CANCEL);
                        return RetryingExecutor.RESULT_CANCEL;
                }
            }
        };

        // Execute the operations
        executor.execute(createAdapter, checkAudience, prepareAdapter);
    }

    /**
     * Checks if the schedule is ready to be executed.
     *
     * @param scheduleId The schedule ID.
     * @return {@code true} to execute the schedule, otherwise {@code false}.
     */
    private boolean isScheduleReady(@NonNull String scheduleId) {
        // Prevent display on pause.
        if (isPaused()) {
            return false;
        }

        AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        return adapterWrapper != null && adapterWrapper.isReady(getContext());
    }

    /**
     * Helper method to display the in-app message.
     *
     * @param scheduleId The schedule ID to display.
     */
    @MainThread
    private void executeSchedule(@NonNull String scheduleId) {
        final AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);

        if (adapterWrapper == null) {
            driver.scheduleExecuted(scheduleId);
            return;
        }

        try {
            adapterWrapper.display(getContext());
        } catch (AdapterWrapper.DisplayException e) {
            Logger.error(e, "Failed to display in-app message: %s, schedule: %s", adapterWrapper.scheduleId, adapterWrapper.message.getId());
            driver.scheduleExecuted(scheduleId);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    adapterWrapper.adapterFinished(getContext());
                }
            });
            return;
        }

        analytics.addEvent(new DisplayEvent(adapterWrapper.message));
        synchronized (listeners) {
            for (InAppMessageListener listener : new ArrayList<>(listeners)) {
                listener.onMessageDisplayed(scheduleId, adapterWrapper.message);
            }
        }

        Logger.verbose("InAppMessagingManager - Message displayed with scheduleId: %s", scheduleId);
    }

    /**
     * Creates an adapter wrapper.
     *
     * @param schedule The schedule.
     * @return The adapter wrapper.
     */
    @Nullable
    private AdapterWrapper createAdapterWrapper(@NonNull InAppMessageSchedule schedule) {
        InAppMessageAdapter adapter = null;
        DisplayCoordinator coordinator = null;

        InAppMessage message = schedule.getInfo().getInAppMessage();

        try {
            message = extendMessage(message);

            InAppMessageAdapter.Factory factory;
            synchronized (adapterFactories) {
                factory = adapterFactories.get(message.getType());
            }

            if (factory == null) {
                Logger.debug("InAppMessageManager - No display adapter for message type: %s. " +
                                "Unable to process schedule: %s message: %s", message.getType(),
                        schedule.getId(), message.getId());
            } else {
                adapter = factory.createAdapter(message);
            }

            OnRequestDisplayCoordinatorCallback displayCoordinatorCallback = this.displayCoordinatorCallback;
            if (displayCoordinatorCallback != null) {
                coordinator = displayCoordinatorCallback.onRequestDisplayCoordinator(message);
            }

            if (coordinator == null) {
                coordinator = this.defaultCoordinator;
            }
        } catch (Exception e) {
            Logger.error(e, "InAppMessageManager - Failed to create in-app message adapter.");
            return null;
        }

        if (adapter == null) {
            Logger.error("InAppMessageManager - Failed to create in-app message adapter.");
            return null;
        }

        coordinator.setDisplayReadyCallback(displayReadyCallback);
        return new AdapterWrapper(schedule.getId(), message, adapter, coordinator);
    }

    /**
     * Extends the in-app message.
     *
     * @param originalMessage The original message.
     * @return The extended message, or the original message if no extender is set.
     */
    @NonNull
    private InAppMessage extendMessage(@NonNull InAppMessage originalMessage) {
        // Extend the message
        InAppMessageExtender extender = InAppMessageManager.this.messageExtender;
        if (extender != null) {
            return extender.extend(originalMessage);
        }

        return originalMessage;
    }

    /**
     * Updates the automation engine pause state with user enable and component enable flags.
     */
    private void updateEnginePauseState() {
        automationEngine.setPaused(!(isEnabled() && isComponentEnabled()));
    }

    /**
     * Called by the display handler when an in-app message is finished.
     *
     * @param scheduleId The schedule ID.
     * @param resolutionInfo Info on why the event is finished.
     * @param displayMilliseconds The display time in milliseconds
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void messageFinished(@NonNull String scheduleId, @NonNull ResolutionInfo resolutionInfo, long displayMilliseconds) {
        Logger.verbose("InAppMessagingManager - Message finished. ScheduleID: %s", scheduleId);

        final AdapterWrapper adapterWrapper = adapterWrappers.remove(scheduleId);

        // No record
        if (adapterWrapper == null) {
            return;
        }

        // Add resolution event
        analytics.addEvent(ResolutionEvent.messageResolution(adapterWrapper.message, resolutionInfo, displayMilliseconds));

        // Run Actions
        InAppActionUtils.runActions(adapterWrapper.message.getActions(), actionRunRequestFactory);

        // Notify any listeners
        synchronized (listeners) {
            for (InAppMessageListener listener : new ArrayList<>(listeners)) {
                listener.onMessageFinished(scheduleId, adapterWrapper.message, resolutionInfo);
            }
        }

        // Finish the schedule
        driver.scheduleExecuted(scheduleId);
        adapterWrapper.displayFinished();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                adapterWrapper.adapterFinished(getContext());
            }
        });

        // Cancel the schedule if a cancel button was tapped
        if (resolutionInfo.getButtonInfo() != null && ButtonInfo.BEHAVIOR_CANCEL.equals(resolutionInfo.getButtonInfo().getBehavior())) {
            cancelSchedule(scheduleId);
        }
    }

    /**
     * Called by the display handler to see if an in-app message is allowed to display.
     *
     * @param scheduleId The schedule ID.
     * @return {@code true} To allow the message to display, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    boolean isDisplayAllowed(String scheduleId) {
        AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        return adapterWrapper != null && adapterWrapper.displayed;
    }
}
