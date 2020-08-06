/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipLoopers;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.auth.AuthException;
import com.urbanairship.automation.auth.AuthManager;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.deferred.DeferredScheduleClient;
import com.urbanairship.automation.tags.TagGroupManager;
import com.urbanairship.automation.tags.TagGroupResult;
import com.urbanairship.automation.tags.TagGroupUtils;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppAutomationScheduler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.RetryingExecutor;

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
public class InAppAutomation extends AirshipComponent implements InAppAutomationScheduler {

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
    private final Handler backgroundHandler;
    private final AirshipChannel airshipChannel;
    private final AutomationEngine automationEngine;
    private final InAppMessageManager inAppMessageManager;
    private final TagGroupManager tagGroupManager;
    private final RetryingExecutor retryingExecutor;
    private final ActionRunRequestFactory actionRunRequestFactory;
    private final DeferredScheduleClient deferredScheduleClient;

    private final AutomationDriver driver = new AutomationDriver() {
        @Override
        public void onPrepareSchedule(@NonNull Schedule schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback callback) {
            InAppAutomation.this.onPrepareSchedule(schedule, triggerContext, callback);
        }

        @Override
        public int onCheckExecutionReadiness(@NonNull Schedule schedule) {
            return InAppAutomation.this.onCheckExecutionReadiness(schedule);
        }

        @Override
        public void onExecuteTriggeredSchedule(@NonNull Schedule schedule, @NonNull ExecutionCallback finishCallback) {
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

        this.automationEngine = new AutomationEngine(context, runtimeConfig, analytics);
        this.airshipChannel = airshipChannel;
        this.tagGroupManager = new TagGroupManager(runtimeConfig, airshipChannel, namedUser, preferenceDataStore);
        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore, remoteData);
        this.inAppMessageManager = new InAppMessageManager(context, preferenceDataStore, analytics, new InAppMessageManager.Delegate() {
            @Override
            public void onReadinessChanged() {
                automationEngine.checkPendingSchedules();
            }
        });

        this.backgroundHandler = new Handler(AirshipLoopers.getBackgroundLooper());
        this.retryingExecutor = RetryingExecutor.newSerialExecutor(Looper.getMainLooper());
        this.actionRunRequestFactory = new ActionRunRequestFactory();

        this.deferredScheduleClient = new DeferredScheduleClient(runtimeConfig, new AuthManager(runtimeConfig, airshipChannel));
    }

    @VisibleForTesting
    InAppAutomation(@NonNull Context context,
                    @NonNull PreferenceDataStore preferenceDataStore,
                    @NonNull AutomationEngine engine,
                    @NonNull AirshipChannel airshipChannel,
                    @NonNull TagGroupManager tagGroupManager,
                    @NonNull InAppRemoteDataObserver observer,
                    @NonNull InAppMessageManager inAppMessageManager,
                    @NonNull RetryingExecutor retryingExecutor,
                    @NonNull ActionRunRequestFactory actionRunRequestFactory,
                    @NonNull DeferredScheduleClient deferredScheduleClient) {
        super(context, preferenceDataStore);
        this.automationEngine = engine;
        this.airshipChannel = airshipChannel;
        this.tagGroupManager = tagGroupManager;
        this.remoteDataSubscriber = observer;
        this.inAppMessageManager = inAppMessageManager;
        this.retryingExecutor = retryingExecutor;
        this.actionRunRequestFactory = actionRunRequestFactory;
        this.deferredScheduleClient = deferredScheduleClient;
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

                Collection<Schedule<? extends ScheduleData>> schedules = getSchedules().get();
                if (schedules == null) {
                    return tags;
                }

                for (Schedule<? extends ScheduleData> schedule : schedules) {
                    Audience audience = schedule.getAudience();
                    if (audience != null && audience.getTagSelector() != null && audience.getTagSelector().containsTagGroups()) {
                        TagGroupUtils.addAll(tags, audience.getTagSelector().getTagGroups());
                    }
                }

                return tags;
            }
        });

        this.automationEngine.setScheduleListener(new AutomationEngine.ScheduleListener() {
            @Override
            public void onScheduleExpired(@NonNull final Schedule<? extends ScheduleData> schedule) {
                if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
                    inAppMessageManager.onMessageScheduleFinished(schedule.getId());
                }
            }

            @Override
            public void onScheduleCancelled(@NonNull final Schedule<? extends ScheduleData> schedule) {
                if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
                    inAppMessageManager.onMessageScheduleFinished(schedule.getId());
                }
            }

            @Override
            public void onScheduleLimitReached(@NonNull final Schedule<? extends ScheduleData> schedule) {
                if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
                    inAppMessageManager.onMessageScheduleFinished(schedule.getId());
                }
            }

            @Override
            public void onNewSchedule(@NonNull final Schedule<? extends ScheduleData> schedule) {
                if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
                    inAppMessageManager.onNewMessageSchedule(schedule.getId(), (InAppMessage) schedule.coerceType());
                }
            }
        });

        automationEngine.start(driver);
        updateEnginePauseState();

        // New user cut off time
        if (remoteDataSubscriber.getScheduleNewUserCutOffTime() == -1) {
            remoteDataSubscriber.setScheduleNewUserCutOffTime(airshipChannel.getId() == null ? System.currentTimeMillis() : 0);
        }
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
        remoteDataSubscriber.subscribe(backgroundHandler.getLooper(), this);
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
     * Gets the in-app message manager.
     *
     * @return The in-app message manager.
     */
    public InAppMessageManager getInAppMessageManager() {
        return inAppMessageManager;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules) {
        return automationEngine.schedule(schedules);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> schedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        return automationEngine.schedule(schedule);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelSchedule(@NonNull String scheduleId) {
        return automationEngine.cancel(Collections.singletonList(scheduleId));
    }

    /**
     * Cancels all schedules of a given type.
     *
     * @param type The type.
     * @return {@code true} if a schedule was cancelled, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PendingResult<Boolean> cancelSchedules(@NonNull @Schedule.Type String type) {
        return automationEngine.cancelByType(type);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelScheduleGroup(@NonNull String group) {
        return automationEngine.cancelGroup(group);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<Actions>>> getActionScheduleGroup(@NonNull final String group) {
        return automationEngine.getSchedules(group, Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Schedule<Actions>> getActionSchedule(@NonNull String scheduleId) {
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<Actions>>> getActionSchedules() {
        return automationEngine.getSchedulesByType(Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<InAppMessage>>> getMessageScheduleGroup(@NonNull String group) {
        return automationEngine.getSchedules(group, Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Schedule<InAppMessage>> getMessageSchedule(@NonNull String scheduleId) {
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<InAppMessage>>> getMessageSchedules() {
        return automationEngine.getSchedulesByType(Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
        return automationEngine.getSchedules();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits) {
        return automationEngine.editSchedule(scheduleId, edits);
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
     * Returns {@code true} if automations are paused, otherwise {@code false}.
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

    @WorkerThread
    private void onPrepareSchedule(final @NonNull Schedule<? extends ScheduleData> schedule,
                                   final @Nullable TriggerContext triggerContext,
                                   final @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        Logger.verbose("InAppAutomation - onPrepareSchedule schedule: %s, trigger context: %s", schedule.getId(), triggerContext);

        if (isScheduleInvalid(schedule)) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the subscriber is already up to date, invalidate immediately
                    if (remoteDataSubscriber.isUpToDate()) {
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
            return;
        }

        // Audience checks
        RetryingExecutor.Operation checkAudience = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                Map<String, Set<String>> tagGroups = null;

                if (schedule.getAudience() == null) {
                    return RetryingExecutor.RESULT_FINISHED;
                }

                if (schedule.getAudience().getTagSelector() != null && schedule.getAudience().getTagSelector().containsTagGroups()) {
                    Map<String, Set<String>> tags = schedule.getAudience().getTagSelector().getTagGroups();
                    TagGroupResult result = tagGroupManager.getTags(tags);
                    if (!result.success) {
                        return RetryingExecutor.RESULT_RETRY;
                    }

                    tagGroups = result.tagGroups;
                }

                if (AudienceChecks.checkAudience(getContext(), schedule.getAudience(), tagGroups)) {
                    return RetryingExecutor.RESULT_FINISHED;
                }

                callback.onFinish(getPrepareResultMissedAudience(schedule));
                return RetryingExecutor.RESULT_CANCEL;
            }
        };

        RetryingExecutor.Operation prepareSchedule = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                switch (schedule.getType()) {
                    case Schedule.TYPE_ACTION:
                        callback.onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
                        return RetryingExecutor.RESULT_FINISHED;

                    case Schedule.TYPE_IN_APP_MESSAGE:
                        inAppMessageManager.onPrepare(schedule.getId(), (InAppMessage) schedule.coerceType(), callback);
                        return RetryingExecutor.RESULT_FINISHED;

                    case Schedule.TYPE_DEFERRED:
                        return onPrepareDeferredSchedule(schedule, triggerContext, callback);
                }

                Logger.error("Unexpected schedule type: %s", schedule.getType());
                return RetryingExecutor.RESULT_FINISHED;
            }
        };

        retryingExecutor.execute(checkAudience, prepareSchedule);
    }

    @RetryingExecutor.Result
    private int onPrepareDeferredSchedule(final @NonNull Schedule<? extends ScheduleData> schedule,
                                          final @Nullable TriggerContext triggerContext,
                                          final @NonNull AutomationDriver.PrepareScheduleCallback callback) {

        Deferred deferredScheduleData = (Deferred) schedule.coerceType();
        Response<DeferredScheduleClient.Result> response;

        String channelId = airshipChannel.getId();
        if (channelId == null) {
            return RetryingExecutor.RESULT_RETRY;
        }

        try {
            response = deferredScheduleClient.performRequest(deferredScheduleData.url, channelId, triggerContext);
        } catch (RequestException e) {
            if (deferredScheduleData.retryOnTimeout) {
                Logger.debug(e, "Failed to resolve deferred schedule, will retry. Schedule: %s", schedule.getId());
                return RetryingExecutor.RESULT_RETRY;
            } else {
                Logger.debug(e, "Failed to resolve deferred schedule. Schedule: %s", schedule.getId());
                callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
                return RetryingExecutor.RESULT_CANCEL;
            }
        } catch (AuthException e) {
            Logger.debug(e, "Failed to resolve deferred schedule: %s", schedule.getId());
            return RetryingExecutor.RESULT_RETRY;
        }

        if (!response.isSuccessful()) {
            Logger.debug("Failed to resolve deferred schedule, will retry. Schedule: %s, Response: %", schedule.getId(), response);
            return RetryingExecutor.RESULT_RETRY;
        }

        if (!response.getResult().isAudienceMatch()) {
            callback.onFinish(getPrepareResultMissedAudience(schedule));
            return RetryingExecutor.RESULT_CANCEL;
        }

        InAppMessage message = response.getResult().getMessage();
        if (message != null) {
            inAppMessageManager.onPrepare(schedule.getId(), message, callback);
        } else {
            // Handled by backend, penalize to count towards limit
            callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        }

        return RetryingExecutor.RESULT_FINISHED;
    }

    @MainThread
    @AutomationDriver.ReadyResult
    private int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule) {
        Logger.verbose("InAppAutomation - onCheckExecutionReadiness schedule: %s", schedule.getId());

        // Prevent display on pause.
        if (isPaused()) {
            return AutomationDriver.READY_RESULT_NOT_READY;
        }

        switch (schedule.getType()) {
            case Schedule.TYPE_ACTION:
                if (isScheduleInvalid(schedule)) {
                    return AutomationDriver.READY_RESULT_INVALIDATE;
                }
                return AutomationDriver.READY_RESULT_CONTINUE;
            case Schedule.TYPE_IN_APP_MESSAGE:
                if (isScheduleInvalid(schedule)) {
                    inAppMessageManager.onExecutionInvalidated(schedule.getId());
                    return AutomationDriver.READY_RESULT_INVALIDATE;
                }
                return inAppMessageManager.onCheckExecutionReadiness(schedule.getId());
        }

        Logger.error("Unexpected schedule type: %s", schedule.getType());
        return AutomationDriver.READY_RESULT_CONTINUE;
    }

    @MainThread
    private void onExecuteTriggeredSchedule(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull AutomationDriver.ExecutionCallback callback) {
        Logger.verbose("InAppAutomation - onExecuteTriggeredSchedule schedule: %s", schedule.getId());

        switch (schedule.getType()) {
            case Schedule.TYPE_ACTION:
                executeActions(schedule, (Actions) schedule.coerceType(), callback);
                break;
            case Schedule.TYPE_IN_APP_MESSAGE:
                inAppMessageManager.onExecute(schedule.getId(), callback);
                break;
            default:
                Logger.error("Unexpected schedule type: %s", schedule.getType());
                callback.onFinish();
                break;
        }
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
    private boolean isScheduleInvalid(@NonNull Schedule<? extends ScheduleData> schedule) {
        return remoteDataSubscriber.isRemoteSchedule(schedule) && !remoteDataSubscriber.isScheduleValid(schedule);
    }

    private void executeActions(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull Actions actions, AutomationDriver.ExecutionCallback callback) {
        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.ACTION_SCHEDULE_ID_METADATA, schedule.getId());

        ActionCallback actionCallback = new ActionCallback(callback, actions.getActionsMap().size());
        for (Map.Entry<String, JsonValue> entry : actions.getActionsMap().entrySet()) {
            actionRunRequestFactory.createActionRequest(entry.getKey())
                                   .setValue(entry.getValue())
                                   .setSituation(Action.SITUATION_AUTOMATION)
                                   .setMetadata(metadata)
                                   .run(Looper.getMainLooper(), actionCallback);
        }
    }

    @AutomationDriver.PrepareResult
    private int getPrepareResultMissedAudience(@NonNull Schedule schedule) {
        @AutomationDriver.PrepareResult int result = AutomationDriver.PREPARE_RESULT_PENALIZE;
        switch (schedule.getAudience().getMissBehavior()) {
            case Audience.MISS_BEHAVIOR_CANCEL:
                result = AutomationDriver.PREPARE_RESULT_CANCEL;
                break;
            case Audience.MISS_BEHAVIOR_SKIP:
                result = AutomationDriver.PREPARE_RESULT_SKIP;
                break;
            case Audience.MISS_BEHAVIOR_PENALIZE:
                result = AutomationDriver.PREPARE_RESULT_PENALIZE;
                break;
        }

        return result;
    }

    /**
     * Helper class that calls the callback after all actions have run.
     */
    private static class ActionCallback implements ActionCompletionCallback {

        private final AutomationDriver.ExecutionCallback callback;
        private int pendingActionCallbacks;

        /**
         * Default constructor.
         *
         * @param callback The completion callback.
         * @param pendingActionCallbacks Number of pending callbacks to expect.
         */
        ActionCallback(AutomationDriver.ExecutionCallback callback, int pendingActionCallbacks) {
            this.callback = callback;
            this.pendingActionCallbacks = pendingActionCallbacks;
        }

        @Override
        public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
            pendingActionCallbacks--;
            if (pendingActionCallbacks == 0) {
                callback.onFinish();
            }
        }

    }

}
