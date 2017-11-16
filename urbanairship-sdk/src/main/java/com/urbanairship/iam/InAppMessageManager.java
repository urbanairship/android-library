/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDataManager;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.iam.banner.BannerAdapterFactory;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * In-app messaging manager.
 */
public class InAppMessageManager extends AirshipComponent implements InAppMessageScheduler {

    /**
     * Fetch retry delay.
     */
    private static final long FETCH_RETRY_DELAY_MS = 30000;

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
    private Map<String, AdapterWrapper> adapterWrappers = new HashMap<>();
    private boolean isDisplayedLocked = false;
    private boolean isAirshipReady = false;
    private final InAppRemoteDataObserver remoteDataSubscriber;

    private final Executor executor;
    private final ActivityMonitor activityMonitor;
    private final RemoteData remoteData;
    private final Analytics analytics;
    private final PushManager pushManager;
    private final Handler mainHandler;
    private final InAppMessageDriver driver;
    private final AutomationEngine<InAppMessageSchedule> automationEngine;
    private final Map<String, InAppMessageAdapter.Factory> adapterFactories = new HashMap<>();


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
                .build();

        setAdapterFactory(InAppMessage.TYPE_BANNER, new BannerAdapterFactory());
    }

    @VisibleForTesting
    InAppMessageManager(PreferenceDataStore preferenceDataStore, Analytics analytics, ActivityMonitor activityMonitor,
                        Executor executor, InAppMessageDriver driver, AutomationEngine<InAppMessageSchedule> engine,
                        RemoteData remoteData, PushManager pushManager) {
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
            public boolean isMessageReady(String scheduleId, InAppMessage message) {
                if (!isAirshipReady) {
                    return false;
                }

                if (!AudienceChecks.checkAudience(UAirship.getApplicationContext(), message.getAudience())) {
                    cancelSchedule(scheduleId);
                    adapterWrappers.remove(scheduleId);
                    return false;
                }

                AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
                if (adapterWrapper == null) {
                    InAppMessageAdapter.Factory factory = adapterFactories.get(message.getType());
                    if (factory == null) {
                        return false;
                    }

                    try {
                        InAppMessageAdapter adapter = factory.createAdapter(message);
                        adapterWrappers.put(scheduleId, new AdapterWrapper(scheduleId, message.getId(), adapter));
                    } catch (Exception e) {
                        Logger.error("InAppMessageManager - Failed to create in-app message adapter.", e);
                        return false;
                    }

                    prepareMessage(scheduleId);
                    return false;
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
            public void onDisplay(String scheduleId) {
                display(getResumedActivity(), scheduleId, false);
            }
        });

        this.automationEngine.setScheduleExpiryListener(new AutomationEngine.ScheduleExpiryListener<InAppMessageSchedule>() {
            @Override
            public void onScheduleExpired(InAppMessageSchedule schedule) {
                analytics.addEvent(ResolutionEvent.messageExpired(schedule.getInfo().getInAppMessage().getId(), schedule.getInfo().getEnd()));
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

        // If the new user cut off time is not set either set it to the max long or 0 depending
        // on if the channel is created or not.
        if (remoteDataSubscriber.getScheduleNewUserCutOffTime() == -1) {
            remoteDataSubscriber.setScheduleNewUserCutOffTime(pushManager.getChannelId() == null ? Long.MAX_VALUE : 0);
        }

        // Add a listener to the remote data to set the new user cut off time to the
        // last modified time if its earlier then the current new user cut off time.
        remoteData.addListener(new RemoteData.Listener() {
            @Override
            public void onDataRefreshed() {
                if (remoteData.getLastModified() == null) {
                    return;
                }

                try {
                    long lastModifiedTime = DateUtils.parseIso8601(remoteData.getLastModified());
                    if (lastModifiedTime <= remoteDataSubscriber.getScheduleNewUserCutOffTime()) {
                        remoteDataSubscriber.setScheduleNewUserCutOffTime(lastModifiedTime);
                    }
                    remoteData.remoteListener(this);
                } catch (ParseException e) {
                    Logger.error("InAppMessageManager - Failed to parse last modified time: " + remoteData.getLastModified(), e);
                }
            }
        });
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
        automationEngine.setPaused(!isEnabled);
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

                int result = adapterWrapper.prepare();

                if (result == InAppMessageAdapter.OK) {
                    automationEngine.checkPendingSchedules();
                } else {
                    // Retry after a delay
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            prepareMessage(scheduleId);
                        }
                    }, FETCH_RETRY_DELAY_MS);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<Collection<InAppMessageSchedule>> schedule(Collection<InAppMessageScheduleInfo> scheduleInfos) {
        return automationEngine.schedule(scheduleInfos);
    }

    /**
     * {@inheritDoc}
     */
    public PendingResult<InAppMessageSchedule> scheduleMessage(InAppMessageScheduleInfo messageScheduleInfo) {
        return automationEngine.schedule(messageScheduleInfo);
    }

    /**
     * {@inheritDoc}
     */
    public PendingResult<Void> cancelSchedule(String scheduleId) {
        return automationEngine.cancel(Collections.singletonList(scheduleId));
    }

    /**
     * {@inheritDoc}
     */
    public PendingResult<Boolean> cancelMessage(String messageId) {
        return automationEngine.cancelGroup(messageId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<Void> cancelMessages(Collection<String> messageIds) {
        return automationEngine.cancelGroups(messageIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PendingResult<Collection<InAppMessage>> getMessages(final String messageId) {
        final PendingResult<Collection<InAppMessage>> pendingResult = new PendingResult<>();

        automationEngine.getSchedules(messageId).addResultCallback(new ResultCallback<List<InAppMessageSchedule>>() {
            @Override
            public void onResult(@Nullable List<InAppMessageSchedule> result) {
                if (result == null || result.isEmpty()) {
                    pendingResult.setResult(Collections.<InAppMessage>emptyList());
                }

                List<InAppMessage> messages = new ArrayList<>();
                for (InAppMessageSchedule schedule : result) {
                    messages.add(schedule.getInfo().getInAppMessage());
                }

                pendingResult.setResult(messages);
            }
        });

        return pendingResult;
    }

    /**
     * Sets a {@link InAppMessageAdapter} for a given display type.
     *
     * @param displayType The display type.
     * @param factory The adapter facotry.
     */
    public void setAdapterFactory(@InAppMessage.DisplayType String displayType, InAppMessageAdapter.Factory factory) {
        if (factory == null) {
            adapterFactories.remove(displayType);
        } else {
            adapterFactories.put(displayType, factory);
        }
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

        analytics.addEvent(ResolutionEvent.messageResolution(adapterWrapper.messageId, resolutionInfo));

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
     * @param isRedisplay {@code true} if the schedule is being redisplayed, otherwise {@code false}.
     */
    @MainThread
    private void display(Activity activity, @NonNull String scheduleId, boolean isRedisplay) {
        AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);

        if (adapterWrapper == null) {
            return;
        }

        if (activity != null && adapterWrapper.display(activity, isRedisplay) == InAppMessageAdapter.OK) {
            Logger.verbose("InAppMessagingManager - Message displayed with scheduleId: " + scheduleId);
            this.currentScheduleId = scheduleId;
            this.isDisplayedLocked = true;
            this.currentActivity = new WeakReference<>(activity);
            mainHandler.removeCallbacks(postDisplayRunnable);
            carryOverScheduleIds.remove(scheduleId);

            if (!isRedisplay) {
                analytics.addEvent(new DisplayEvent(adapterWrapper.messageId));
            }
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
     * Helper class that keeps track of the schedule's adapter, assets, and execution callback.
     */
    public static final class AdapterWrapper {
        private final String scheduleId;
        private final String messageId;
        public volatile boolean isReady;
        public InAppMessageAdapter adapter;

        public AdapterWrapper(@NonNull String scheduleId, @NonNull String messageId, @NonNull InAppMessageAdapter adapter) {
            this.scheduleId = scheduleId;
            this.messageId = messageId;
            this.adapter = adapter;
        }

        private int prepare() {
            try {
                int result = adapter.onPrepare(UAirship.getApplicationContext());
                if (result == InAppMessageAdapter.OK) {
                    isReady = true;
                }
                return result;
            } catch (Exception e) {
                Logger.error("InAppMessageManager - Failed to prepare in-app message.", e);
                return InAppMessageAdapter.RETRY;
            }
        }

        private int display(Activity activity, boolean isRedisplay) {
            try {
                DisplayHandler displayHandler = new DisplayHandler(scheduleId);
                return adapter.onDisplay(activity, isRedisplay, displayHandler);
            } catch (Exception e) {
                Logger.error("InAppMessageManager - Failed to display in-app message.", e);
                return InAppMessageAdapter.RETRY;
            }
        }

        private void finish() {
            try {
                adapter.onFinish();
            } catch (Exception e) {
                Logger.error("InAppMessageManager - Exception during onFinish().", e);
            }
        }
    }

}
