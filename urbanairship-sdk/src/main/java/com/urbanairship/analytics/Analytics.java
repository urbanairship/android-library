/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.location.LocationRequestOptions;
import com.urbanairship.location.RegionEvent;
import com.urbanairship.util.Checks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class is the primary interface to the UrbanAirship Analytics API.
 */
public class Analytics extends AirshipComponent {

    /**
     * Job action to send an event.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String ACTION_SEND = "ACTION_SEND";

    /**
     * Job action to update the ad ID on foreground.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String ACTION_UPDATE_ADVERTISING_ID = "ACTION_UPDATE_ADVERTISING_ID";


    /**
     * Minimum amount of delay when {@link #uploadEvents()} is called.
     */
    public static final long SCHEDULE_SEND_DELAY_SECONDS = 10;

    private static final String KEY_PREFIX = "com.urbanairship.analytics";
    private static final String ANALYTICS_ENABLED_KEY = KEY_PREFIX + ".ANALYTICS_ENABLED";
    private static final String ASSOCIATED_IDENTIFIERS_KEY = KEY_PREFIX + ".ASSOCIATED_IDENTIFIERS";
    private static final String ADVERTISING_ID_AUTO_TRACKING_KEY = KEY_PREFIX + ".ADVERTISING_ID_TRACKING";

    private final PreferenceDataStore preferenceDataStore;
    private final Context context;
    private final JobDispatcher jobDispatcher;
    private final ActivityMonitor activityMonitor;
    private final EventManager eventManager;
    private final ActivityMonitor.Listener listener;
    private final int platform;
    private final AirshipConfigOptions configOptions;
    private final Executor executor;
    private final List<AnalyticsListener> analyticsListeners = new ArrayList<>();
    private final Object associatedIdentifiersLock = new Object();

    private AnalyticsJobHandler analyticsJobHandler;

    // Session state
    private String sessionId;
    private String conversionSendId;
    private String conversionMetadata;

    // Screen state
    private String currentScreen;
    private String previousScreen;
    private long screenStartTime;

    /**
     * The Analytics constructor.
     *
     * @param builder The builder instance.
     */
    private Analytics(Builder builder) {
        this.context = builder.context.getApplicationContext();
        this.preferenceDataStore = builder.preferenceDataStore;
        this.configOptions = builder.configOptions;
        this.platform = builder.platform;
        this.jobDispatcher = builder.jobDispatcher;
        this.activityMonitor = builder.activityMonitor;
        this.eventManager = builder.eventManager;
        this.executor = builder.executor == null ? Executors.newSingleThreadExecutor() : builder.executor;

        this.listener = new ActivityMonitor.Listener() {
            @Override
            public void onForeground(final long time) {
                Analytics.this.onForeground(time);
            }

            @Override
            public void onBackground(final long time) {
                Analytics.this.onBackground(time);
            }
        };
    }

    @Override
    protected void init() {
        startNewSession();

        activityMonitor.addListener(listener);

        if (activityMonitor.isAppForegrounded()) {
            onForeground(System.currentTimeMillis());
        }
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeListener(listener);
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @JobInfo.JobResult
    public int onPerformJob(@NonNull UAirship airship, JobInfo jobInfob) {
        if (analyticsJobHandler == null) {
            analyticsJobHandler = new AnalyticsJobHandler(context, airship, eventManager);
        }

        return analyticsJobHandler.performJob(jobInfob);
    }

    /**
     * Determines if the application is in the foreground.
     *
     * @return <code>true</code> if the application is in the foreground, otherwise
     * <code>false</code>.
     */
    public boolean isAppInForeground() {
        return activityMonitor.isAppForegrounded();
    }

    /**
     * Adds an analytics event.
     *
     * @param event The event to be triggered.
     */
    public void addEvent(@NonNull final Event event) {
        //noinspection ConstantConditions
        if (event == null || !event.isValid()) {
            Logger.warn("Analytics - Invalid event: " + event);
            return;
        }

        if (!isEnabled()) {
            Logger.debug("Analytics disabled - ignoring event: " + event.getType());
            return;
        }

        Logger.verbose("Analytics - Adding event: " + event.getType());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                eventManager.addEvent(event, sessionId);
            }
        });

        applyListeners(event);
    }

    /**
     * Records a location.
     *
     * @param location The location to record.
     */
    public void recordLocation(@NonNull Location location) {
        recordLocation(location, null, LocationEvent.UPDATE_TYPE_SINGLE);
    }

    /**
     * Records a location.
     *
     * @param location The new location.
     * @param options The location request options.
     * @param updateType The update type.
     */
    public void recordLocation(@NonNull Location location, @Nullable LocationRequestOptions options, @LocationEvent.UpdateType int updateType) {
        int requestedAccuracy;
        int distance;

        if (options == null) {
            requestedAccuracy = -1;
            distance = -1;
        } else {
            distance = (int) options.getMinDistance();
            if (options.getPriority() == LocationRequestOptions.PRIORITY_HIGH_ACCURACY) {
                requestedAccuracy = Criteria.ACCURACY_FINE;
            } else {
                requestedAccuracy = Criteria.ACCURACY_COARSE;
            }
        }

        LocationEvent event = new LocationEvent(location, updateType, requestedAccuracy, distance, isAppInForeground());
        addEvent(event);
    }

    /**
     * Returns the last stored send Id from when a push conversion was detected.
     *
     * @return A send Id String.
     * @hide
     */
    public String getConversionSendId() {
        return conversionSendId;
    }

    /**
     * Stores the send id for later retrieval when a push conversion has been detected.
     * You should not call this method directly.
     *
     * @param sendId The associated send Id String.
     * @hide
     */
    public void setConversionSendId(@Nullable String sendId) {
        Logger.debug("Analytics - Setting conversion send ID: " + sendId);
        this.conversionSendId = sendId;
    }

    /**
     * Returns the last stored send metadata from when a push conversion was detected.
     *
     * @return A metadata String.
     * @hide
     */
    public String getConversionMetadata() {
        return conversionMetadata;
    }

    /**
     * Stores the send metadata for later retrieval when a push conversion has been detected.
     * You should not call this method directly.
     *
     * @param metadata The associated metadata String.
     * @hide
     */
    public void setConversionMetadata(@Nullable String metadata) {
        Logger.debug("Analytics - Setting conversion metadata: " + metadata);
        this.conversionMetadata = metadata;
    }

    /**
     * Gets the current environment Id.
     *
     * @return A environment Id String.
     */
    String getSessionId() {
        return sessionId;
    }

    /**
     * Called when the app is foregrounded.
     *
     * @param timeMS Time of foregrounding.
     */
    void onForeground(long timeMS) {
        // Start a new environment when the app enters the foreground
        startNewSession();

        // If the app backgrounded, there should be no current screen
        if (currentScreen == null) {
            trackScreen(previousScreen);
        }

        // If advertising ID tracking is enabled, dispatch a job to update the advertising ID.
        if (isAutoTrackAdvertisingIdEnabled()) {
            jobDispatcher.dispatch(JobInfo.newBuilder()
                                          .setAction(ACTION_UPDATE_ADVERTISING_ID)
                                          .setId(JobInfo.ANALYTICS_UPDATE_ADVERTISING_ID)
                                          .setAirshipComponent(Analytics.class)
                                          .build());
        }

        addEvent(new AppForegroundEvent(timeMS));
    }

    /**
     * Called when the app is backgrounded.
     *
     * @param timeMS Time of backgrounding.
     */
    void onBackground(long timeMS) {
        // Stop tracking screen
        trackScreen(null);

        addEvent(new AppBackgroundEvent(timeMS));
        setConversionSendId(null);
        setConversionMetadata(null);
    }

    /**
     * Starts a new session.
     */
    void startNewSession() {
        sessionId = UUID.randomUUID().toString();
        Logger.debug("Analytics - New session: " + sessionId);
    }

    /**
     * Sets analytics enabled. When disabling analytics, any locally stored events will be deleted.
     * </p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @param enabled {@code true} to enable analytics, {@code false} to disable.
     */
    public void setEnabled(boolean enabled) {
        boolean previousValue = preferenceDataStore.getBoolean(ANALYTICS_ENABLED_KEY, true);

        // When we disable analytics delete all the events
        if (previousValue && !enabled) {

            Logger.info("Deleting all analytic events.");

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    eventManager.deleteEvents();
                }
            });
        }

        preferenceDataStore.put(ANALYTICS_ENABLED_KEY, enabled);
    }

    /**
     * Returns {@code true} if analytics is enabled and {@link com.urbanairship.AirshipConfigOptions#analyticsEnabled}
     * is set to {@code true}, otherwise {@code false}.
     * </p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @return {@code true} if analytics is enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return configOptions.analyticsEnabled && preferenceDataStore.getBoolean(ANALYTICS_ENABLED_KEY, true);
    }

    /**
     * Sets the ad ID auto tracking enabled flag.
     *
     * @param enabled {@code true} to enable, {@code false} to disable.
     */
    public void setAutoTrackAdvertisingIdEnabled(boolean enabled) {
        if (platform == UAirship.ANDROID_PLATFORM && !PlayServicesUtils.isGoogleAdsDependencyAvailable() && enabled) {
            Logger.error("Analytics - Advertising ID auto-tracking could not be enabled due to a missing Google Ads dependency.");
            return;
        }

        preferenceDataStore.put(ADVERTISING_ID_AUTO_TRACKING_KEY, enabled);

        if (enabled) {
            jobDispatcher.dispatch(JobInfo.newBuilder()
                                          .setAction(ACTION_UPDATE_ADVERTISING_ID)
                                          .setId(JobInfo.ANALYTICS_UPDATE_ADVERTISING_ID)
                                          .setAirshipComponent(Analytics.class)
                                          .build());
        }
    }

    /**
     * Returns the ad ID auto tracking enabled flag.
     *
     * @return {@code true} if enabled, otherwise {@code false}.
     */
    public boolean isAutoTrackAdvertisingIdEnabled() {
        return preferenceDataStore.getBoolean(ADVERTISING_ID_AUTO_TRACKING_KEY, false);
    }

    /**
     * Edits the currently stored associated identifiers. All changes made in the editor are batched,
     * and not stored until you call apply(). Calling apply() on the editor will associate the
     * identifiers with the device and add an event that will be sent up with other analytics
     * events. See {@link com.urbanairship.analytics.AssociatedIdentifiers.Editor}
     *
     * @return The AssociatedIdentifiers.Editor
     */
    public AssociatedIdentifiers.Editor editAssociatedIdentifiers() {
        return new AssociatedIdentifiers.Editor() {
            @Override
            void onApply(boolean clear, Map<String, String> idsToAdd, List<String> idsToRemove) {
                synchronized (associatedIdentifiersLock) {
                    Map<String, String> ids = new HashMap<>();
                    if (!clear) {
                        Map<String, String> currentIds = getAssociatedIdentifiers().getIds();
                        ids.putAll(currentIds);
                    }

                    ids.putAll(idsToAdd);

                    for (String key : idsToRemove) {
                        ids.remove(key);
                    }

                    AssociatedIdentifiers identifiers = new AssociatedIdentifiers(ids);
                    preferenceDataStore.put(ASSOCIATED_IDENTIFIERS_KEY, identifiers);
                    addEvent(new AssociateIdentifiersEvent(identifiers));
                }
            }
        };
    }

    /**
     * Returns the device's current associated identifiers.
     *
     * @return The current associated identifiers.
     */
    public AssociatedIdentifiers getAssociatedIdentifiers() {
        synchronized (associatedIdentifiersLock) {
            try {
                return AssociatedIdentifiers.fromJson(preferenceDataStore.getString(ASSOCIATED_IDENTIFIERS_KEY, null));
            } catch (JsonException e) {
                Logger.debug("Unable to parse associated identifiers.", e);
                preferenceDataStore.remove(ASSOCIATED_IDENTIFIERS_KEY);
            }

            return new AssociatedIdentifiers();
        }
    }

    /**
     * Initiates screen tracking for a specific app screen, must be called once per tracked screen.
     *
     * @param screen The screen's string identifier.
     */
    public void trackScreen(@Nullable String screen) {
        // Prevent duplicate calls to track same screen
        if (currentScreen != null && currentScreen.equals(screen)) {
            return;
        }

        // If there's a screen currently being tracked set its stop time and add it to analytics
        if (currentScreen != null) {
            ScreenTrackingEvent ste = new ScreenTrackingEvent(currentScreen, previousScreen, screenStartTime, System.currentTimeMillis());

            // Set previous screen to last tracked screen
            previousScreen = currentScreen;

            // Add screen tracking event to next analytics batch
            addEvent(ste);
        }

        currentScreen = screen;

        if (screen != null) {
            for (AnalyticsListener listener : new ArrayList<>(analyticsListeners)) {
                listener.onScreenTracked(screen);
            }
        }

        screenStartTime = System.currentTimeMillis();
    }

    /**
     * Uploads any pending events. Events are batched and uploaded automatically to conserve
     * battery life. Normally apps should not call this method directly.
     */
    public void uploadEvents() {
        eventManager.scheduleEventUpload(SCHEDULE_SEND_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Adds an {@link AnalyticsListener} for analytics events.
     *
     * @param analyticsListener The {@link AnalyticsListener}.
     */
    public void addAnalyticsListener(AnalyticsListener analyticsListener) {
        synchronized (analyticsListeners) {
            analyticsListeners.add(analyticsListener);
        }
    }

    /**
     * Removes an {@link AnalyticsListener} for analytics events.
     *
     * @param analyticsListener The {@link AnalyticsListener}.
     */
    public void removeAnalyticsListener(AnalyticsListener analyticsListener) {
        synchronized (analyticsListeners) {
            analyticsListeners.remove(analyticsListener);
        }
    }

    /**
     * Applies the set {@link AnalyticsListener} instances to an event.
     *
     * @param event The event.
     */
    private void applyListeners(Event event) {
        for (AnalyticsListener listener : new ArrayList<>(analyticsListeners)) {
            switch (event.getType()) {
                case CustomEvent.TYPE:
                    if (event instanceof CustomEvent) {
                        listener.onCustomEventAdded((CustomEvent) event);
                    }
                    break;
                case RegionEvent.TYPE:
                    if (event instanceof RegionEvent) {
                        listener.onRegionEventAdded((RegionEvent) event);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static class Builder {

        private PreferenceDataStore preferenceDataStore;
        private Context context;
        private JobDispatcher jobDispatcher;
        private ActivityMonitor activityMonitor;
        private EventManager eventManager;
        private int platform;
        private AirshipConfigOptions configOptions;
        private Executor executor;


        /**
         * Builder constructor.
         *
         * @param context The application context.
         */
        public Builder(@NonNull Context context) {
            this.context = context.getApplicationContext();
        }

        /**
         * Sets the {@link PreferenceDataStore}.
         *
         * @param preferenceDataStore The {@link PreferenceDataStore}.
         * @return The builder instance.
         */
        public Builder setPreferenceDataStore(@NonNull PreferenceDataStore preferenceDataStore) {
            this.preferenceDataStore = preferenceDataStore;
            return this;
        }

        /**
         * Sets the {@link JobDispatcher}.
         *
         * @param jobDispatcher The {@link JobDispatcher}.
         * @return The builder instance.
         */
        public Builder setJobDispatcher(@NonNull JobDispatcher jobDispatcher) {
            this.jobDispatcher = jobDispatcher;
            return this;
        }

        /**
         * Sets the {@link ActivityMonitor}.
         *
         * @param activityMonitor The {@link ActivityMonitor}.
         * @return The builder instance.
         */
        public Builder setActivityMonitor(@NonNull ActivityMonitor activityMonitor) {
            this.activityMonitor = activityMonitor;
            return this;
        }

        /**
         * Sets the {@link EventManager}.
         *
         * @param eventManager The {@link EventManager}.
         * @return The builder instance.
         */
        public Builder setEventManager(@NonNull EventManager eventManager) {
            this.eventManager = eventManager;
            return this;
        }

        /**
         * Sets the device platform.
         *
         * @param platform The device's platform.
         * @return The builder instance.
         */
        public Builder setPlatform(@UAirship.Platform int platform) {
            this.platform = platform;
            return this;
        }

        /**
         * Sets the {@link AirshipConfigOptions}.
         *
         * @param configOptions The {@link AirshipConfigOptions}.
         * @return The builder instance.
         */
        public Builder setConfigOptions(@NonNull AirshipConfigOptions configOptions) {
            this.configOptions = configOptions;
            return this;
        }

        /**
         * Sets the analytics executor.
         *
         * @param executor The analytics executor.
         * @return The builder instance.
         */
        public Builder setExecutor(@NonNull Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds the analytics instance.
         *
         * @return The analytics instance.
         */
        public Analytics build() {
            Checks.checkNotNull(context, "Missing context.");
            Checks.checkNotNull(jobDispatcher, "Missing job dispatcher.");
            Checks.checkNotNull(activityMonitor, "Missing activity monitor.");
            Checks.checkNotNull(eventManager, "Missing event manager.");
            Checks.checkNotNull(configOptions, "Missing config options.");
            return new Analytics(this);
        }
    }
}
