/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.os.Handler;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipLoopers;
import com.urbanairship.AlarmOperationScheduler;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDataManager;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.AutomationEngine;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.iam.tags.TagGroupUtils;
import com.urbanairship.json.JsonMap;
import com.urbanairship.remotedata.RemoteData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * In-app automation.
 */
public class InAppAutomation extends AirshipComponent implements InAppMessageScheduler {

    /**
     * Preference key for enabling/disabling in-app automation.
     */
    private final static String ENABLE_KEY = "com.urbanairship.iam.enabled";

    /**
     * Preference key for pausing/unpausing in-app automation.
     */
    private final static String PAUSE_KEY = "com.urbanairship.iam.paused";

    // State
    private final InAppRemoteDataObserver remoteDataSubscriber;
    private final RemoteData remoteData;
    private final Handler backgroundHandler;
    private final AirshipChannel airshipChannel;
    private final AutomationEngine<InAppMessageSchedule> automationEngine;
    private final InAppMessageManager inAppMessageManager;
    private final TagGroupManager tagGroupManager;

    private final InAppMessageDriver driver = new InAppMessageDriver() {
        @Override
        public void onPrepareSchedule(@NonNull InAppMessageSchedule schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback callback) {
            InAppAutomation.this.onPrepareSchedule(schedule, triggerContext, callback);
        }

        @Override
        public int onCheckExecutionReadiness(@NonNull InAppMessageSchedule schedule) {
            return InAppAutomation.this.onCheckExecutionReadiness(schedule);
        }

        @Override
        public void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull ExecutionCallback finishCallback) {
            InAppAutomation.this.onExecuteTriggeredSchedule(schedule, finishCallback);
        }
    };

    /**
     * Gets the shared In-App Automation instance.
     *
     * @return The shared In-App Automation instance.
     */
    @NonNull
    public static InAppAutomation shared() {
        return UAirship.shared().requireComponent(InAppAutomation.class);
    }

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param runtimeConfig The runtime config.
     * @param analytics Analytics instance.
     * @param remoteData Remote data.
     * @param airshipChannel The airship channel.
     * @param namedUser The named user.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public InAppAutomation(@NonNull Context context,
                           @NonNull PreferenceDataStore preferenceDataStore,
                           @NonNull AirshipRuntimeConfig runtimeConfig,
                           @NonNull Analytics analytics,
                           @NonNull RemoteData remoteData,
                           @NonNull AirshipChannel airshipChannel,
                           @NonNull NamedUser namedUser) {
        super(context, preferenceDataStore);

        this.remoteData = remoteData;
        this.airshipChannel = airshipChannel;

        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore);
        this.backgroundHandler = new Handler(AirshipLoopers.getBackgroundLooper());
        this.automationEngine = new AutomationEngine.Builder<InAppMessageSchedule>()
                .setAnalytics(analytics)
                .setActivityMonitor(InAppActivityMonitor.shared(context))
                .setDataManager(new AutomationDataManager(context, runtimeConfig.getConfigOptions().appKey, "in-app"))
                .setScheduleLimit(200)
                .setOperationScheduler(AlarmOperationScheduler.shared(context))
                .build();
        this.tagGroupManager = new TagGroupManager(runtimeConfig, airshipChannel, namedUser, preferenceDataStore);
        this.inAppMessageManager = new InAppMessageManager(context, preferenceDataStore, analytics, tagGroupManager, new InAppMessageManager.Delegate() {
            @Override
            public void onReadinessChanged() {
                automationEngine.checkPendingSchedules();
            }
        });
    }

    @VisibleForTesting
    InAppAutomation(@NonNull Context context,
                    @NonNull PreferenceDataStore preferenceDataStore,
                    @NonNull AutomationEngine<InAppMessageSchedule> engine,
                    @NonNull RemoteData remoteData,
                    @NonNull AirshipChannel airshipChannel,
                    @NonNull TagGroupManager tagGroupManager,
                    @NonNull InAppRemoteDataObserver observer,
                    @NonNull InAppMessageManager inAppMessageManager) {
        super(context, preferenceDataStore);
        this.remoteData = remoteData;
        this.airshipChannel = airshipChannel;
        this.remoteDataSubscriber = observer;
        this.automationEngine = engine;
        this.tagGroupManager = tagGroupManager;
        this.inAppMessageManager = inAppMessageManager;

        this.backgroundHandler = new Handler(AirshipLoopers.getBackgroundLooper());
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void init() {
        super.init();
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

        this.automationEngine.setScheduleListener(new AutomationEngine.ScheduleListener<InAppMessageSchedule>() {
            @Override
            public void onScheduleExpired(@NonNull final InAppMessageSchedule schedule) {
                inAppMessageManager.onScheduleExpired(schedule);
            }

            @Override
            public void onScheduleCancelled(@NonNull final InAppMessageSchedule schedule) {
                inAppMessageManager.onScheduleCancelled(schedule);
            }

            @Override
            public void onScheduleLimitReached(@NonNull final InAppMessageSchedule schedule) {
                inAppMessageManager.onScheduleLimitReached(schedule);
            }

            @Override
            public void onNewSchedule(@NonNull final InAppMessageSchedule schedule) {
                inAppMessageManager.onNewSchedule(schedule);
            }
        });

        automationEngine.start(driver);
        updateEnginePauseState();

        // New user cut off time
        if (remoteDataSubscriber.getScheduleNewUserCutOffTime() == -1) {
            remoteDataSubscriber.setScheduleNewUserCutOffTime(airshipChannel.getId() == null ? System.currentTimeMillis() : 0);
        }
    }

    @WorkerThread
    private void onPrepareSchedule(final @NonNull InAppMessageSchedule schedule,
                                   final @Nullable TriggerContext triggerContext,
                                   final @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        Logger.verbose("InAppAutomation - onPrepareSchedule schedule: %s, trigger context: %s", schedule.getId(), triggerContext);

        if (isScheduleInvalid(schedule)) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the subscriber is already up to date, invalidate immediately
                    if (remoteData.isMetadataCurrent(remoteDataSubscriber.getLastPayloadMetadata())) {
                        callback.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                    } else {
                        // Otherwise wait to invalidate
                        remoteDataSubscriber.addListener(new InAppRemoteDataObserver.Listener() {
                            @Override
                            public void onSchedulesUpdated() {
                                callback.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                                remoteDataSubscriber.removeListener(this);
                            }
                        });
                    }
                }
            });
        } else {
            inAppMessageManager.onPrepare(schedule.getId(), schedule.getInfo().getInAppMessage(),  callback);
        }
    }

    @MainThread
    @AutomationDriver.ReadyResult
    private int onCheckExecutionReadiness(@NonNull InAppMessageSchedule schedule) {
        Logger.verbose("InAppAutomation - onCheckExecutionReadiness schedule: %s", schedule.getId());

        // Prevent display on pause.
        if (isPaused()) {
            return AutomationDriver.READY_RESULT_NOT_READY;
        }

        if (isScheduleInvalid(schedule)) {
            inAppMessageManager.onExecutionInvalidated(schedule.getId());
            return AutomationDriver.READY_RESULT_INVALIDATE;
        }

        return inAppMessageManager.onCheckExecutionReadiness(schedule.getId());
    }

    @MainThread
    private void onExecuteTriggeredSchedule(@NonNull InAppMessageSchedule schedule, @NonNull AutomationDriver.ExecutionCallback callback) {
        Logger.verbose("InAppAutomation - onExecuteTriggeredSchedule schedule: %s", schedule.getId());
        inAppMessageManager.onExecute(schedule.getId(), callback);
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.IN_APP;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
        remoteDataSubscriber.subscribe(remoteData, backgroundHandler.getLooper(), this);
        automationEngine.checkPendingSchedules();
        inAppMessageManager.onAirshipReady();
    }

    @Override
    protected void tearDown() {
        super.tearDown();
        remoteDataSubscriber.cancel();
        automationEngine.stop();
    }

    @Override
    public void onNewConfig(@Nullable JsonMap configValue) {
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonMap(configValue);
        tagGroupManager.setEnabled(config.tagGroupsConfig.isEnabled);
        tagGroupManager.setCacheStaleReadTime(config.tagGroupsConfig.cacheStaleReadTimeSeconds, TimeUnit.SECONDS);
        tagGroupManager.setPreferLocalTagDataTime(config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds, TimeUnit.SECONDS);
        tagGroupManager.setCacheMaxAgeTime(config.tagGroupsConfig.cacheMaxAgeInSeconds, TimeUnit.SECONDS);
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
        return automationEngine.schedule(scheduleInfos, JsonMap.EMPTY_MAP);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos, @NonNull JsonMap metadata) {
        return automationEngine.schedule(scheduleInfos, metadata);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo) {
        return automationEngine.schedule(messageScheduleInfo, JsonMap.EMPTY_MAP);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo, @NonNull JsonMap metadata) {
        return automationEngine.schedule(messageScheduleInfo, metadata);
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
     * Pauses or unpauses automations.
     *
     * @param paused {@code true} to pause in-app automations, otherwise {@code false}.
     */
    public void setPaused(boolean paused) {
        boolean storedPausedState = getDataStore().getBoolean(PAUSE_KEY, paused);

        // Only update when paused state transitions from paused to unpaused
        if (storedPausedState && !paused) {
            automationEngine.checkPendingSchedules();
        }

        getDataStore().put(PAUSE_KEY, paused);
    }

    /**
     * Returns {@code true} if automatinos are paused, otherwise {@code false}.
     *
     * @return {@code true} automations are paused, otherwise {@code false}.
     */
    public boolean isPaused() {
        return getDataStore().getBoolean(PAUSE_KEY, false);
    }

    /**
     * Enables or disables automations.
     *
     * @param enabled {@code true} to enable automations, otherwise {@code false}.
     */
    public void setEnabled(boolean enabled) {
        getDataStore().put(ENABLE_KEY, enabled);
        updateEnginePauseState();
    }

    /**
     * Returns {@code true} if in-app automation is enabled, {@code false} if its disabled.
     *
     * @return {@code true} if in-app automation is enabled, {@code false} if its disabled.
     */
    public boolean isEnabled() {
        return getDataStore().getBoolean(ENABLE_KEY, true);
    }

    /**
     * Updates the automation engine pause state with user enable and component enable flags.
     */
    private void updateEnginePauseState() {
        automationEngine.setPaused(!(isEnabled() && isComponentEnabled()));
    }

    /**
     * Checks to see if a schedule from remote-data is still valid by checking
     * the schedule metadata.
     *
     * @param schedule The in-app schedule.
     * @return {@code true} if the schedule is valid, otherwise {@code false}.
     */
    private boolean isScheduleInvalid(@NonNull InAppMessageSchedule schedule) {
        if (!InAppMessage.SOURCE_REMOTE_DATA.equals(schedule.getInfo().getInAppMessage().getSource())) {
            return false;
        }

        return !remoteData.isMetadataCurrent(schedule.getMetadata());
    }

    public InAppMessageManager getInAppMessageManager() {
        return inAppMessageManager;
    }

}
