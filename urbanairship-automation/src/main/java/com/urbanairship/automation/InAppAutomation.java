/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.UALog;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.audience.AudienceOverrides;
import com.urbanairship.audience.AudienceOverridesProvider;
import com.urbanairship.audience.DeviceInfoProvider;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.deferred.DeferredScheduleClient;
import com.urbanairship.automation.limits.FrequencyChecker;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.automation.limits.FrequencyLimitManager;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.experiment.ExperimentManager;
import com.urbanairship.experiment.ExperimentResult;
import com.urbanairship.experiment.MessageInfo;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppAutomationScheduler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.meteredusage.AirshipMeteredUsage;
import com.urbanairship.meteredusage.MeteredUsageEventEntity;
import com.urbanairship.meteredusage.MeteredUsageType;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataInfo;
import com.urbanairship.util.Clock;
import com.urbanairship.util.RetryingExecutor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-app automation.
 */
public class InAppAutomation extends AirshipComponent implements InAppAutomationScheduler {

    /**
     * Preference key for pausing/unpausing in-app automation.
     */
    private final static String PAUSE_KEY = "com.urbanairship.iam.paused";

    // State
    private final InAppRemoteDataObserver remoteDataSubscriber;
    private final AirshipChannel airshipChannel;

    private final Contact contact;
    private final AutomationEngine automationEngine;
    private final InAppMessageManager inAppMessageManager;
    private final RetryingExecutor retryingExecutor;
    private final DeferredScheduleClient deferredScheduleClient;
    private final FrequencyLimitManager frequencyLimitManager;
    private final PrivacyManager privacyManager;

    private final AirshipMeteredUsage meteredUsage;

    private final ActionsScheduleDelegate actionScheduleDelegate;
    private final InAppMessageScheduleDelegate inAppMessageScheduleDelegate;

    private final Map<String, ScheduleDelegate<?>> scheduleDelegateMap = new HashMap<>();
    private final Map<String, FrequencyChecker> frequencyCheckerMap = new HashMap<>();
    private final Map<String, RemoteDataInfo> remoteDataInfoMap = new HashMap<>();

    private final Map<String, Uri> redirectURLs = new HashMap<>();

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private Cancelable subscription;

    private final AudienceOverridesProvider audienceOverridesProvider;
    private final ExperimentManager experimentManager;
    private final DeviceInfoProvider infoProvider;

    private final AirshipRuntimeConfig config;
    private final Clock clock;
    private final Executor backgroundExecutor;

    private final AutomationDriver driver = new AutomationDriver() {
        @Override
        public void onScheduleExecutionInterrupted(Schedule<? extends ScheduleData> schedule) {
            InAppAutomation.this.onScheduleExecutionInterrupted(schedule);
        }

        @Override
        public void onPrepareSchedule(@NonNull Schedule<? extends ScheduleData> schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback callback) {
            InAppAutomation.this.onPrepareSchedule(schedule, triggerContext, callback);
        }

        @Override
        public int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule) {
            return InAppAutomation.this.onCheckExecutionReadiness(schedule);
        }

        @Override
        public void onExecuteTriggeredSchedule(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull ExecutionCallback finishCallback) {
            InAppAutomation.this.onExecuteTriggeredSchedule(schedule, finishCallback);
        }
    };

    private final InAppRemoteDataObserver.Delegate remoteDataObserverDelegate = new InAppRemoteDataObserver.Delegate() {
        @Override
        @NonNull
        public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
            return InAppAutomation.this.getSchedules();
        }

        @Override
        @NonNull
        public PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits) {
            return InAppAutomation.this.editSchedule(scheduleId, edits);
        }

        @NonNull
        @Override
        public PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules) {
            return InAppAutomation.this.schedule(schedules);
        }

        @Override
        public Future<Boolean> updateConstraints(@NonNull Collection<FrequencyConstraint> constraints) {
            return frequencyLimitManager.updateConstraints(constraints);
        }
    };

    private final PrivacyManager.Listener privacyManagerListener = () -> {
        checkUpdatesSubscription();
        updateEnginePauseState();
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
     * @param privacyManager The privacy manager.
     * @param analytics Analytics instance.
     * @param remoteData Remote data.
     * @param airshipChannel The airship channel.
     * @param audienceOverridesProvider The audience overrides provider.
     * @param experimentManager The experiment manager instance.
     * @param infoProvider The device info provider.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public InAppAutomation(@NonNull Context context,
                           @NonNull PreferenceDataStore preferenceDataStore,
                           @NonNull AirshipRuntimeConfig runtimeConfig,
                           @NonNull PrivacyManager privacyManager,
                           @NonNull Analytics analytics,
                           @NonNull RemoteData remoteData,
                           @NonNull AirshipChannel airshipChannel,
                           @NonNull AudienceOverridesProvider audienceOverridesProvider,
                           @NonNull ExperimentManager experimentManager,
                           @NonNull DeviceInfoProvider infoProvider,
                           @NonNull AirshipMeteredUsage meteredUsage,
                           @NonNull Contact contact) {
        super(context, preferenceDataStore);
        this.privacyManager = privacyManager;
        this.automationEngine = new AutomationEngine(context, runtimeConfig, analytics, preferenceDataStore);
        this.airshipChannel = airshipChannel;
        this.remoteDataSubscriber = new InAppRemoteDataObserver(context, preferenceDataStore, remoteData);
        this.inAppMessageManager = new InAppMessageManager(context, preferenceDataStore, analytics, automationEngine::checkPendingSchedules);
        this.retryingExecutor = RetryingExecutor.newSerialExecutor(Looper.getMainLooper());
        this.deferredScheduleClient = new DeferredScheduleClient(runtimeConfig);
        this.actionScheduleDelegate = new ActionsScheduleDelegate();
        this.inAppMessageScheduleDelegate = new InAppMessageScheduleDelegate(inAppMessageManager);
        this.frequencyLimitManager = new FrequencyLimitManager(context, runtimeConfig);
        this.audienceOverridesProvider = audienceOverridesProvider;
        this.config = runtimeConfig;
        this.experimentManager = experimentManager;
        this.infoProvider = infoProvider;
        this.meteredUsage = meteredUsage;
        this.clock = Clock.DEFAULT_CLOCK;
        this.backgroundExecutor = AirshipExecutors.newSerialExecutor();
        this.contact = contact;
    }

    @VisibleForTesting
    InAppAutomation(@NonNull Context context,
                    @NonNull PreferenceDataStore preferenceDataStore,
                    @NonNull AirshipRuntimeConfig runtimeConfig,
                    @NonNull PrivacyManager privacyManager,
                    @NonNull AutomationEngine engine,
                    @NonNull AirshipChannel airshipChannel,
                    @NonNull InAppRemoteDataObserver observer,
                    @NonNull InAppMessageManager inAppMessageManager,
                    @NonNull RetryingExecutor retryingExecutor,
                    @NonNull DeferredScheduleClient deferredScheduleClient,
                    @NonNull ActionsScheduleDelegate actionsScheduleDelegate,
                    @NonNull InAppMessageScheduleDelegate inAppMessageScheduleDelegate,
                    @NonNull FrequencyLimitManager frequencyLimitManager,
                    @NonNull AudienceOverridesProvider audienceOverridesProvider,
                    @NonNull ExperimentManager experimentManager,
                    @NonNull DeviceInfoProvider infoProvider,
                    @NonNull AirshipMeteredUsage meteredUsage,
                    @NonNull Clock clock,
                    @NonNull Executor executor,
                    @NonNull Contact contact) {

        super(context, preferenceDataStore);
        this.privacyManager = privacyManager;
        this.automationEngine = engine;
        this.airshipChannel = airshipChannel;
        this.remoteDataSubscriber = observer;
        this.inAppMessageManager = inAppMessageManager;
        this.retryingExecutor = retryingExecutor;
        this.deferredScheduleClient = deferredScheduleClient;
        this.actionScheduleDelegate = actionsScheduleDelegate;
        this.inAppMessageScheduleDelegate = inAppMessageScheduleDelegate;
        this.frequencyLimitManager = frequencyLimitManager;
        this.audienceOverridesProvider = audienceOverridesProvider;
        this.config = runtimeConfig;
        this.experimentManager = experimentManager;
        this.infoProvider = infoProvider;
        this.meteredUsage = meteredUsage;
        this.clock = clock;
        this.backgroundExecutor = executor;
        this.contact = contact;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void init() {
        super.init();

        if (config.getConfigOptions().autoPauseInAppAutomationOnLaunch) {
            this.setPaused(true);
        }
        this.automationEngine.setScheduleListener(new AutomationEngine.ScheduleListener() {
            @Override
            public void onScheduleExpired(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onScheduleFinished(schedule);
                }
            }

            @Override
            public void onScheduleCancelled(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onScheduleFinished(schedule);
                }
            }

            @Override
            public void onScheduleLimitReached(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onScheduleFinished(schedule);
                }
            }

            @Override
            public void onNewSchedule(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onNewSchedule(schedule);
                }
            }
        });
        updateEnginePauseState();
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
        inAppMessageManager.onAirshipReady();
        privacyManager.addListener(privacyManagerListener);
        checkUpdatesSubscription();
    }

    @Override
    protected void tearDown() {
        super.tearDown();
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
        automationEngine.stop();
        isStarted.set(false);
        privacyManager.removeListener(privacyManagerListener);
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
        ensureStarted();
        return automationEngine.schedule(schedules);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> schedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        ensureStarted();
        return automationEngine.schedule(schedule);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelSchedule(@NonNull String scheduleId) {
        ensureStarted();
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
        ensureStarted();
        return automationEngine.cancelByType(type);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelScheduleGroup(@NonNull String group) {
        ensureStarted();
        return automationEngine.cancelGroup(group);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<Actions>>> getActionScheduleGroup(@NonNull final String group) {
        ensureStarted();
        return automationEngine.getSchedules(group, Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Schedule<Actions>> getActionSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<Actions>>> getActionSchedules() {
        ensureStarted();
        return automationEngine.getSchedulesByType(Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<InAppMessage>>> getMessageScheduleGroup(@NonNull String group) {
        ensureStarted();
        return automationEngine.getSchedules(group, Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Schedule<InAppMessage>> getMessageSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<InAppMessage>>> getMessageSchedules() {
        ensureStarted();
        return automationEngine.getSchedulesByType(Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public PendingResult<Schedule<Deferred>> getDeferredMessageSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_DEFERRED);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public PendingResult<Schedule<? extends ScheduleData>> getSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
        ensureStarted();
        return automationEngine.getSchedules();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits) {
        ensureStarted();
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
     * @deprecated Enable/disable by enabling {@link PrivacyManager#FEATURE_IN_APP_AUTOMATION} in {@link PrivacyManager}.
     * This will call through to the privacy manager
     */
    @Deprecated
    public void setEnabled(boolean enabled) {
        if (enabled) {
            privacyManager.enable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        } else {
            privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        }
    }

    /**
     * Returns {@code true} if in-app automation is enabled, {@code false} if its disabled.
     *
     * @return {@code true} if in-app automation is enabled, {@code false} if its disabled.
     * @deprecated Use {@link PrivacyManager#isEnabled(int...)} to check {@link PrivacyManager#FEATURE_IN_APP_AUTOMATION}.
     */
    @Deprecated
    public boolean isEnabled() {
        return privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
    }

    @WorkerThread
    private void onPrepareSchedule(final @NonNull Schedule<? extends ScheduleData> schedule,
                                   final @Nullable TriggerContext triggerContext,
                                   final @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        UALog.v("onPrepareSchedule schedule: %s, trigger context: %s", schedule.getId(), triggerContext);

        final AutomationDriver.PrepareScheduleCallback callbackWrapper = result -> {
            if (result != AutomationDriver.PREPARE_RESULT_CONTINUE) {
                frequencyCheckerMap.remove(schedule.getId());
                remoteDataInfoMap.remove(schedule.getId());
            }
            callback.onFinish(result);
        };

        RetryingExecutor.Operation checkValid = () -> {
            if (remoteDataSubscriber.requiresRefresh(schedule)) {
                remoteDataSubscriber.waitFullRefresh(schedule, () -> {
                    callbackWrapper.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                });
                return RetryingExecutor.cancelResult();
            }

            if (!remoteDataSubscriber.bestEffortRefresh(schedule)) {
                callbackWrapper.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                return RetryingExecutor.cancelResult();
            }

            RemoteDataInfo info = remoteDataSubscriber.parseRemoteDataInfo(schedule);
            if (info != null) {
                remoteDataInfoMap.put(schedule.getId(), info);
            }


            return RetryingExecutor.finishedResult();
        };

        RetryingExecutor.Operation frequencyChecks = () -> {
            if (!schedule.getFrequencyConstraintIds().isEmpty()) {
                FrequencyChecker frequencyChecker = getFrequencyChecker(schedule);
                if (frequencyChecker == null) {
                    return RetryingExecutor.retryResult();
                }
                frequencyCheckerMap.put(schedule.getId(), frequencyChecker);
                if (frequencyChecker.isOverLimit()) {
                    // The frequency constraint is exceeded, skip
                    callbackWrapper.onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
                }
            }

            return RetryingExecutor.finishedResult();
        };

        // Audience checks
        RetryingExecutor.Operation audienceChecks = () -> {
            if (schedule.getAudienceSelector() == null) {
                return RetryingExecutor.finishedResult();
            }

            RemoteDataInfo info = remoteDataSubscriber.parseRemoteDataInfo(schedule);
            String contactId = info == null ? null : info.getContactId();

            PendingResult<Boolean> result = schedule.getAudienceSelector().evaluateAsPendingResult(
                    getContext(), schedule.getNewUserEvaluationDate(), infoProvider, contactId);
            try {
                if (Boolean.TRUE.equals(result.get())) { return RetryingExecutor.finishedResult(); }
            } catch (Exception ignore) { }

            callbackWrapper.onFinish(getPrepareResultMissedAudience(schedule));
            return RetryingExecutor.cancelResult();
        };

        PendingResult<ExperimentResult> experimentResults = new PendingResult<>();
        RetryingExecutor.Operation evaluateExperiments = () -> {
            try {
                ExperimentResult result = evaluateExperiments(schedule);
                experimentResults.setResult(result);
                return RetryingExecutor.finishedResult();
            } catch (Exception ex) {
                UALog.e(ex, "Error on evaluating experiments for schedule " + schedule.getId());
                return RetryingExecutor.retryResult();
            }
        };

        RetryingExecutor.Operation prepareSchedule = () -> {
            switch (schedule.getType()) {
                case Schedule.TYPE_DEFERRED:
                    return resolveDeferred(schedule, triggerContext, experimentResults.getResult(), callbackWrapper);
                case Schedule.TYPE_ACTION:
                    prepareSchedule(schedule, schedule.coerceType(), experimentResults.getResult(), actionScheduleDelegate, callbackWrapper);
                    break;
                case Schedule.TYPE_IN_APP_MESSAGE:
                    prepareSchedule(schedule, schedule.coerceType(), experimentResults.getResult(), inAppMessageScheduleDelegate, callbackWrapper);
                    break;
            }

            return RetryingExecutor.finishedResult();
        };

        RetryingExecutor.Operation[] operations = new RetryingExecutor.Operation[] {
                checkValid,
                frequencyChecks,
                audienceChecks,
                evaluateExperiments,
                prepareSchedule };
        retryingExecutor.execute(operations);
    }

    private @Nullable ExperimentResult evaluateExperiments(
            final @NonNull Schedule<? extends ScheduleData> schedule ) throws ExecutionException, InterruptedException {

        RemoteDataInfo remoteDataInfo = remoteDataSubscriber.parseRemoteDataInfo(schedule);

        // Skip actions for now
        if (schedule.getType().equals(Schedule.TYPE_ACTION)) {
            return null;
        }

        if (schedule.isBypassHoldoutGroups()) {
            return null;
        }

        MessageInfo messageInfo = new MessageInfo(schedule.getMessageType(), schedule.getCampaigns());


        return experimentManager
                .evaluateGlobalHoldoutsPendingResult(
                        messageInfo,
                        remoteDataInfo == null ? null : remoteDataInfo.getContactId()
                )
                .get();
    }

    private <T extends ScheduleData> void prepareSchedule(
            final Schedule<? extends ScheduleData> schedule,
            T scheduleData,
            ExperimentResult experimentResult,
            final ScheduleDelegate<T> delegate,
            final @NonNull AutomationDriver.PrepareScheduleCallback callback) {

        delegate.onPrepareSchedule(schedule, scheduleData, experimentResult, result -> {
            if (result == AutomationDriver.PREPARE_RESULT_CONTINUE) {
                scheduleDelegateMap.put(schedule.getId(), delegate);
            }
            callback.onFinish(result);
        });
    }

    private RetryingExecutor.Result resolveDeferred(final @NonNull Schedule<? extends ScheduleData> schedule,
                                                    final @Nullable TriggerContext triggerContext,
                                                    final @Nullable ExperimentResult experimentResult,
                                                    final @NonNull AutomationDriver.PrepareScheduleCallback callback) {

        Deferred deferredScheduleData = schedule.coerceType();
        Response<DeferredScheduleClient.Result> response;

        String channelId = airshipChannel.getId();
        if (channelId == null) {
            return RetryingExecutor.retryResult();
        }

        Uri url = redirectURLs.containsKey(schedule.getId()) ? redirectURLs.get(schedule.getId()) : deferredScheduleData.getUrl();

        AudienceOverrides.Channel channelOverrides = audienceOverridesProvider.channelOverridesSync(channelId);

        try {
            response = deferredScheduleClient.performRequest(url,
                    channelId, triggerContext, channelOverrides.getTags(),
                    channelOverrides.getAttributes());
        } catch (RequestException e) {
            if (deferredScheduleData.getRetryOnTimeout()) {
                UALog.d(e, "Failed to resolve deferred schedule, will retry. Schedule: %s", schedule.getId());
                return RetryingExecutor.retryResult();
            } else {
                UALog.d(e, "Failed to resolve deferred schedule. Schedule: %s", schedule.getId());
                callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
                return RetryingExecutor.cancelResult();
            }
        }

        DeferredScheduleClient.Result apiResult = response.getResult();

        // Success
        if (response.isSuccessful() && response.getResult() != null) {
            if (!apiResult.isAudienceMatch()) {
                callback.onFinish(getPrepareResultMissedAudience(schedule));
                return RetryingExecutor.cancelResult();
            }

            InAppMessage message = apiResult.getMessage();
            if (message != null) {
                prepareSchedule(schedule, message, experimentResult, inAppMessageScheduleDelegate, callback);
            } else {
                // Handled by backend, penalize to count towards limit
                callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
            }

            return RetryingExecutor.finishedResult();
        }

        UALog.d("Failed to resolve deferred schedule. Schedule: %s, Response: %s", schedule.getId(), response.getResult());

        Uri location = response.getLocationHeader();
        long retryAfter = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1);

        // Error
        switch (response.getStatus()) {
            case 401:
                return RetryingExecutor.retryResult();

            case 409:
                remoteDataSubscriber.notifyOutdated(schedule);
                callback.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                return RetryingExecutor.cancelResult();
            case 429:
                if (location != null) {
                    redirectURLs.put(schedule.getId(), location);
                }
                return retryAfter >= 0 ? RetryingExecutor.retryResult(retryAfter) : RetryingExecutor.retryResult();
            case 307:
                if (location != null) {
                    redirectURLs.put(schedule.getId(), location);
                }
                return retryAfter >= 0 ? RetryingExecutor.retryResult(retryAfter) : RetryingExecutor.retryResult(0);
            default:
                return RetryingExecutor.retryResult();
        }
    }

    @MainThread
    @AutomationDriver.ReadyResult
    private int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule) {
        UALog.v("onCheckExecutionReadiness schedule: %s", schedule.getId());

        // Prevent display on pause.
        if (isPaused()) {
            return AutomationDriver.READY_RESULT_NOT_READY;
        }

        RemoteDataInfo info = remoteDataSubscriber.parseRemoteDataInfo(schedule);

        if ((info != null && !info.equals(remoteDataInfoMap.get(schedule.getId()))) || !this.remoteDataSubscriber.isScheduleValid(schedule)) {
            ScheduleDelegate<?> delegate = scheduleDelegateMap.remove(schedule.getId());
            if (delegate != null) {
                delegate.onExecutionInvalidated(schedule);
            }
            return AutomationDriver.READY_RESULT_INVALIDATE;
        }

        ScheduleDelegate<?> delegate = scheduleDelegateMap.get(schedule.getId());
        if (delegate == null) {
            return AutomationDriver.READY_RESULT_NOT_READY;
        }

        int result = delegate.onCheckExecutionReadiness(schedule);
        if (result != AutomationDriver.READY_RESULT_CONTINUE) {
            return result;
        }

        FrequencyChecker frequencyChecker = frequencyCheckerMap.get(schedule.getId());
        if (frequencyChecker != null && !frequencyChecker.checkAndIncrement()) {
            delegate.onExecutionInvalidated(schedule);
            return AutomationDriver.READY_RESULT_SKIP;
        }

        return AutomationDriver.READY_RESULT_CONTINUE;
    }

    @MainThread
    private void onExecuteTriggeredSchedule(@NonNull Schedule<? extends
            ScheduleData> schedule, @NonNull AutomationDriver.ExecutionCallback callback) {
        UALog.v("onExecuteTriggeredSchedule schedule: %s", schedule.getId());
        frequencyCheckerMap.remove(schedule.getId());
        remoteDataInfoMap.remove(schedule.getId());

        ScheduleDelegate<?> delegate = scheduleDelegateMap.remove(schedule.getId());
        if (delegate != null) {
            delegate.onExecute(schedule, callback);
            reportMeteredUsage(schedule);
        } else {
            UALog.e("Unexpected schedule type: %s", schedule.getType());
            callback.onFinish();
        }
    }

    private void reportMeteredUsage(Schedule<? extends ScheduleData> schedule) {
        if (TextUtils.isEmpty(schedule.getProductId())) {
            return;
        }

        RemoteDataInfo info = remoteDataSubscriber.parseRemoteDataInfo(schedule);
        String contactId = info == null ? null : info.getContactId();
        contactId = contactId == null ? contact.getLastContactId() : contactId;

        final MeteredUsageEventEntity event = new MeteredUsageEventEntity(
                UUID.randomUUID().toString(), schedule.getId(), MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
                schedule.getProductId(), schedule.getReportingContext(), clock.currentTimeMillis(),
                contactId);

        backgroundExecutor.execute(() -> meteredUsage.addEvent(event));
    }

    private void onScheduleExecutionInterrupted(Schedule<? extends ScheduleData> schedule) {
        UALog.v("onScheduleExecutionInterrupted schedule: %s", schedule.getId());
        ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
        if (delegate != null) {
            delegate.onExecutionInterrupted(schedule);
        }
    }

    /**
     * Updates the automation engine pause state with user enable and component enable flags.
     */
    private void updateEnginePauseState() {
        boolean isEnabled = privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION) && isComponentEnabled();
        automationEngine.setPaused(!isEnabled);
    }

    /**
     * Gets the Frequency Checker
     *
     * @param schedule The in-app schedule.
     * @return a FrequencyChecker
     */
    @Nullable
    private FrequencyChecker getFrequencyChecker(@NonNull Schedule<? extends ScheduleData> schedule) {
        try {
            return frequencyLimitManager.getFrequencyChecker(schedule.getFrequencyConstraintIds()).get();
        } catch (InterruptedException | ExecutionException e) {
            UALog.e("InAppAutomation - Failed to get Frequency Limit Checker : " + e);
        }
        return null;
    }

    @AutomationDriver.PrepareResult
    private int getPrepareResultMissedAudience(@NonNull Schedule<? extends ScheduleData> schedule) {
        @AutomationDriver.PrepareResult int result = AutomationDriver.PREPARE_RESULT_PENALIZE;
        if (schedule.getAudienceSelector() == null) {
            return result;
        }

        switch (schedule.getAudienceSelector().getMissBehavior()) {
            case CANCEL:
                result = AutomationDriver.PREPARE_RESULT_CANCEL;
                break;
            case SKIP:
                result = AutomationDriver.PREPARE_RESULT_SKIP;
                break;
            case PENALIZE:
                result = AutomationDriver.PREPARE_RESULT_PENALIZE;
                break;
        }

        return result;
    }

    @Nullable
    private ScheduleDelegate<? extends ScheduleData> delegateForSchedule(Schedule<? extends ScheduleData> schedule) {
        ScheduleDelegate<? extends ScheduleData> delegate = null;

        switch (schedule.getType()) {
            case Schedule.TYPE_ACTION:
                delegate = actionScheduleDelegate;
                break;

            case Schedule.TYPE_DEFERRED:
                Deferred deferred = schedule.coerceType();
                if (Deferred.TYPE_IN_APP_MESSAGE.equals(deferred.getType())) {
                    delegate = inAppMessageScheduleDelegate;
                }
                break;

            case Schedule.TYPE_IN_APP_MESSAGE:
                delegate = inAppMessageScheduleDelegate;
                break;
        }

        return delegate;
    }

    private void ensureStarted() {
        if (!isStarted.getAndSet(true)) {
            UALog.v("Starting In-App automation");
            automationEngine.start(driver);
        }
    }

    private void checkUpdatesSubscription() {
        synchronized (remoteDataObserverDelegate) {
            if (privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION)) {
                ensureStarted();
                if (subscription == null) {
                    subscription = remoteDataSubscriber.subscribe(remoteDataObserverDelegate);
                }
            } else if (subscription != null) {
                subscription.cancel();
                subscription = null;
            }
        }
    }
}
