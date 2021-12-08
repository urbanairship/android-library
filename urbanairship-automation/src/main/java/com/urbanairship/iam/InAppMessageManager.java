/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.os.Looper;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.AutomationDriver;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.iam.assets.AssetManager;
import com.urbanairship.iam.banner.BannerAdapterFactory;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.iam.fullscreen.FullScreenAdapterFactory;
import com.urbanairship.iam.html.HtmlAdapterFactory;
import com.urbanairship.iam.layout.AirshipLayoutAdapterFactory;
import com.urbanairship.iam.modal.ModalAdapterFactory;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.RetryingExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * In-app messaging manager.
 */
public class InAppMessageManager {

    /**
     * IAM delegate
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Delegate {

        void onReadinessChanged();

    }

    /**
     * Default delay between displaying in-app messages.
     */
    public static final long DEFAULT_DISPLAY_INTERVAL_MS = 30000;
    /**
     * Preference key for display interval of in-app automation
     */
    private static final String DISPLAY_INTERVAL_KEY = "com.urbanairship.iam.displayinterval";

    // State
    private final Map<String, AdapterWrapper> adapterWrappers = Collections.synchronizedMap(new HashMap<String, AdapterWrapper>());

    private final RetryingExecutor executor;
    private final ActionRunRequestFactory actionRunRequestFactory;
    private final Analytics analytics;

    private final Map<String, InAppMessageAdapter.Factory> adapterFactories = new HashMap<>();
    private final List<InAppMessageListener> listeners = new ArrayList<>();
    private final DefaultDisplayCoordinator defaultCoordinator;
    private final ImmediateDisplayCoordinator immediateDisplayCoordinator;
    private final AssetManager assetManager;
    private final Context context;
    private final PreferenceDataStore dataStore;
    private final Delegate delegate;

    @Nullable
    private InAppMessageExtender messageExtender;

    @Nullable
    private OnRequestDisplayCoordinatorCallback displayCoordinatorCallback;

    private final DisplayCoordinator.OnDisplayReadyCallback displayReadyCallback = new DisplayCoordinator.OnDisplayReadyCallback() {
        @Override
        public void onReady() {
            delegate.onReadinessChanged();
        }
    };

    private final Map<String, AutomationDriver.ExecutionCallback> executionCallbacks = new HashMap<>();

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public InAppMessageManager(@NonNull Context context,
                               @NonNull PreferenceDataStore dataStore,
                               @NonNull Analytics analytics,
                               @NonNull Delegate delegate) {
        this(context, dataStore, analytics, RetryingExecutor.newSerialExecutor(Looper.getMainLooper()),
                new ActionRunRequestFactory(), new AssetManager(context), delegate);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    InAppMessageManager(@NonNull Context context,
                        @NonNull PreferenceDataStore dataStore,
                        @NonNull Analytics analytics,
                        @NonNull RetryingExecutor executor,
                        @NonNull ActionRunRequestFactory runRequestFactory,
                        @NonNull AssetManager assetManager,
                        @NonNull Delegate delegate) {

        this.context = context;
        this.dataStore = dataStore;
        this.analytics = analytics;
        this.executor = executor;
        this.assetManager = assetManager;
        this.delegate = delegate;
        this.actionRunRequestFactory = runRequestFactory;

        this.defaultCoordinator = new DefaultDisplayCoordinator(getDisplayInterval());
        this.immediateDisplayCoordinator = new ImmediateDisplayCoordinator();

        executor.setPaused(true);
        setAdapterFactory(InAppMessage.TYPE_BANNER, new BannerAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_FULLSCREEN, new FullScreenAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_MODAL, new ModalAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_HTML, new HtmlAdapterFactory());
        setAdapterFactory(InAppMessage.TYPE_AIRSHIP_LAYOUT, new AirshipLayoutAdapterFactory());

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
        dataStore.put(DISPLAY_INTERVAL_KEY, timeUnit.toMillis(time));
        this.defaultCoordinator.setDisplayInterval(time, timeUnit);
    }

    /**
     * Gets the display interval in milliseconds.
     *
     * @return The display interval in milliseconds.
     */
    public long getDisplayInterval() {
        return dataStore.getLong(InAppMessageManager.DISPLAY_INTERVAL_KEY, InAppMessageManager.DEFAULT_DISPLAY_INTERVAL_MS);
    }

    /**
     * Gets the asset manager.
     *
     * @return The asset manager.
     */
    @NonNull
    public AssetManager getAssetManager() {
        return assetManager;
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
     * Sets the message extender. The message will be extended before asset caching, or before
     * the message is prepared for display.
     *
     * @param extender The extender.
     */
    public void setMessageExtender(@Nullable InAppMessageExtender extender) {
        this.messageExtender = extender;
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
     * Called by {@link InAppAutomation} when airship is ready.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onAirshipReady() {
        executor.setPaused(false);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onPrepare(final @NonNull String scheduleId,
                          final @Nullable JsonValue campaigns,
                          final @Nullable JsonValue reportingContext,
                          final @NonNull InAppMessage inAppMessage,
                          final @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        final AdapterWrapper adapter = createAdapterWrapper(scheduleId, campaigns, reportingContext, inAppMessage);
        if (adapter == null) {
            // Failed
            callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
            return;
        }

        // Prepare Assets
        RetryingExecutor.Operation prepareAssets = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                int result = assetManager.onPrepare(scheduleId, adapter.message);

                switch (result) {
                    case AssetManager.PREPARE_RESULT_OK:
                        Logger.debug("Assets prepared for schedule %s.", scheduleId);
                        return RetryingExecutor.RESULT_FINISHED;

                    case AssetManager.PREPARE_RESULT_RETRY:
                        Logger.debug("Assets failed to prepare for schedule %s. Will retry.", scheduleId);
                        return RetryingExecutor.RESULT_RETRY;

                    case AssetManager.PREPARE_RESULT_CANCEL:
                    default:
                        Logger.debug("Assets failed to prepare. Cancelling display for schedule %s.", scheduleId);
                        assetManager.onDisplayFinished(scheduleId, adapter.message);
                        callback.onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
                        return RetryingExecutor.RESULT_CANCEL;
                }
            }
        };

        // Prepare Adapter
        RetryingExecutor.Operation prepareAdapter = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                int result = adapter.prepare(context, assetManager.getAssets(scheduleId));

                switch (result) {
                    case InAppMessageAdapter.OK:
                        Logger.debug("Adapter prepared schedule %s.", scheduleId);

                        // Store the adapter
                        adapterWrappers.put(scheduleId, adapter);
                        callback.onFinish(AutomationDriver.PREPARE_RESULT_CONTINUE);
                        return RetryingExecutor.RESULT_FINISHED;

                    case InAppMessageAdapter.RETRY:
                        Logger.debug("Adapter failed to prepare schedule %s. Will retry.", scheduleId);
                        return RetryingExecutor.RESULT_RETRY;

                    case InAppMessageAdapter.CANCEL:
                    default:
                        Logger.debug("Adapter failed to prepare. Cancelling display for schedule %s.", scheduleId);
                        callback.onFinish(AutomationDriver.PREPARE_RESULT_CANCEL);
                        return RetryingExecutor.RESULT_CANCEL;
                }
            }
        };

        // Execute the operations
        executor.execute(prepareAssets, prepareAdapter);
    }

    /**
     * @hide
     */
    @MainThread
    @AutomationDriver.ReadyResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onCheckExecutionReadiness(@NonNull String scheduleId) {
        AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        if (adapterWrapper == null) {
            Logger.error("Missing adapter for schedule %.", scheduleId);
            return AutomationDriver.READY_RESULT_INVALIDATE;
        }

        if (adapterWrapper.isReady(context)) {
            return AutomationDriver.READY_RESULT_CONTINUE;
        } else {
            return AutomationDriver.READY_RESULT_NOT_READY;
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    public void onExecute(@NonNull String scheduleId, @NonNull AutomationDriver.ExecutionCallback callback) {
        final AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        if (adapterWrapper == null) {
            Logger.error("Missing adapter for schedule %.", scheduleId);
            callback.onFinish();
            return;
        }

        synchronized (executionCallbacks) {
            executionCallbacks.put(scheduleId, callback);
        }

        try {
            adapterWrapper.display(context);
        } catch (AdapterWrapper.DisplayException e) {
            Logger.error(e, "Failed to display in-app message for schedule %s.", scheduleId);
            callExecutionFinishedCallback(scheduleId);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    adapterWrapper.adapterFinished(context);
                }
            });
            return;
        }

        if (adapterWrapper.message.isReportingEnabled()) {
            InAppReportingEvent.display(scheduleId, adapterWrapper.message)
                               .setCampaigns(adapterWrapper.campaigns)
                               .setReportingContext(adapterWrapper.reportingContext)
                               .record(analytics);
        }

        synchronized (listeners) {
            for (InAppMessageListener listener : new ArrayList<>(listeners)) {
                listener.onMessageDisplayed(scheduleId, adapterWrapper.message);
            }
        }

        Logger.verbose("Message displayed for schedule %s.", scheduleId);
    }

    /**
     * Called by {@link InAppAutomation} when an execution is no longer valid.
     *
     * @param scheduleId The schedule Id.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onExecutionInvalidated(@NonNull final String scheduleId) {
        final AdapterWrapper adapterWrapper = adapterWrappers.remove(scheduleId);
        if (adapterWrapper == null) {
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                assetManager.onDisplayFinished(scheduleId, adapterWrapper.message);
            }
        });
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onExecutionInterrupted(@NonNull final String scheduleId, @Nullable final JsonValue campaigns, @Nullable final JsonValue reportingContext, @Nullable final InAppMessage message) {
        executor.execute(() -> {
            // Null message must be deferred
            if (message == null || message.isReportingEnabled()) {
                String source = message != null ? message.getSource() : InAppMessage.SOURCE_REMOTE_DATA;
                InAppReportingEvent.interrupted(scheduleId, source)
                                   .setReportingContext(reportingContext)
                                   .setCampaigns(campaigns)
                                   .record(analytics);
            }
        });
    }

    /**
     * Called by the display handler to generate resolution event for standard adapters.
     *
     * @param scheduleId The schedule ID.
     * @param resolutionInfo Info on why the event is finished.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void onResolution(@NonNull String scheduleId, @NonNull ResolutionInfo resolutionInfo, long displayTime) {
        Logger.verbose("Message finished for schedule %s.", scheduleId);

        final AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);

        // No record
        if (adapterWrapper == null) {
            return;
        }

        if (adapterWrapper.message.isReportingEnabled()) {
            InAppReportingEvent.resolution(scheduleId, adapterWrapper.message, displayTime, resolutionInfo)
                               .setCampaigns(adapterWrapper.campaigns)
                               .setReportingContext(adapterWrapper.reportingContext)
                               .record(analytics);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void onDisplayFinished(@NonNull String scheduleId, @NonNull ResolutionInfo resolutionInfo) {
        Logger.verbose("Message finished for schedule %s.", scheduleId);

        final AdapterWrapper adapterWrapper = adapterWrappers.remove(scheduleId);

        // No record
        if (adapterWrapper == null) {
            return;
        }

        // Run Actions
        InAppActionUtils.runActions(adapterWrapper.message.getActions(), actionRunRequestFactory);

        // Notify any listeners
        synchronized (listeners) {
            for (InAppMessageListener listener : new ArrayList<>(listeners)) {
                listener.onMessageFinished(scheduleId, adapterWrapper.message, resolutionInfo);
            }
        }

        // Finish the schedule
        callExecutionFinishedCallback(scheduleId);
        adapterWrapper.displayFinished();
        executor.execute(() -> {
            adapterWrapper.adapterFinished(context);
            // Notify the asset manager
            assetManager.onDisplayFinished(adapterWrapper.scheduleId, adapterWrapper.message);
        });
    }


    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @MainThread
    void onAddEvent(@NonNull String scheduleId, @NonNull InAppReportingEvent event) {
        final AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        // No record
        if (adapterWrapper == null) {
            return;
        }

        if (adapterWrapper.message.isReportingEnabled()) {
            event.setCampaigns(adapterWrapper.campaigns)
                 .setReportingContext(adapterWrapper.reportingContext)
                 .record(analytics);
        }
    }

    private void addEvent(AdapterWrapper adapterWrapper, InAppReportingEvent event) {
        if (adapterWrapper.message.isReportingEnabled()) {
            event.setCampaigns(adapterWrapper.campaigns)
                 .setReportingContext(adapterWrapper.reportingContext)
                 .record(analytics);
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
    boolean isDisplayAllowed(@NonNull String scheduleId) {
        AdapterWrapper adapterWrapper = adapterWrappers.get(scheduleId);
        return adapterWrapper != null && adapterWrapper.displayed;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onMessageScheduleFinished(@NonNull final String scheduleId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                assetManager.onFinish(scheduleId);
            }
        });
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onNewMessageSchedule(@NonNull final String scheduleId, @NonNull final InAppMessage message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                assetManager.onSchedule(scheduleId, new Callable<InAppMessage>() {
                    @Override
                    public InAppMessage call() {
                        return extendMessage(message);
                    }
                });
            }
        });
    }

    /**
     * Creates an adapter wrapper.
     *
     * @param scheduleId The schedule Id.
     * @param campaigns The campaign info.
     * @param campaigns The reporting context.
     * @param message The message.
     * @return The adapter wrapper.
     */
    @Nullable
    private AdapterWrapper createAdapterWrapper(@NonNull String scheduleId,
                                                @Nullable JsonValue campaigns,
                                                @Nullable JsonValue reportingContext,
                                                @NonNull InAppMessage message) {
        InAppMessageAdapter adapter = null;
        DisplayCoordinator coordinator = null;

        try {
            message = extendMessage(message);

            InAppMessageAdapter.Factory factory;
            synchronized (adapterFactories) {
                factory = adapterFactories.get(message.getType());
            }

            if (factory == null) {
                Logger.debug("InAppMessageManager - No display adapter for message type: %s. " +
                        "Unable to process schedule: %s.", message.getType(), scheduleId);
            } else {
                adapter = factory.createAdapter(message);
            }

            OnRequestDisplayCoordinatorCallback displayCoordinatorCallback = this.displayCoordinatorCallback;
            if (displayCoordinatorCallback != null) {
                coordinator = displayCoordinatorCallback.onRequestDisplayCoordinator(message);
            }

            if (coordinator == null) {
                switch (message.getDisplayBehavior()) {
                    case InAppMessage.DISPLAY_BEHAVIOR_IMMEDIATE:
                        coordinator = this.immediateDisplayCoordinator;
                        break;
                    case InAppMessage.DISPLAY_BEHAVIOR_DEFAULT:
                    default:
                        coordinator = this.defaultCoordinator;
                        break;
                }
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
        return new AdapterWrapper(scheduleId, campaigns, reportingContext, message, adapter, coordinator);
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
     * Called to finish the display of an in-app message.
     *
     * @param scheduleId The schedule ID.
     */
    private void callExecutionFinishedCallback(final String scheduleId) {
        synchronized (executionCallbacks) {
            final AutomationDriver.ExecutionCallback callback = executionCallbacks.remove(scheduleId);
            if (callback != null) {
                callback.onFinish();
            }
        }
    }
}
