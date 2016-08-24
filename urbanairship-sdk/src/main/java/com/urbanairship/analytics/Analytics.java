/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.LifeCycleCallbacks;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.json.JsonException;
import com.urbanairship.location.LocationRequestOptions;
import com.urbanairship.location.RegionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This class is the primary interface to the UrbanAirship Analytics API.
 */
public class Analytics extends AirshipComponent {

    private static final String KEY_PREFIX = "com.urbanairship.analytics";
    private static final String ANALYTICS_ENABLED_KEY = KEY_PREFIX + ".ANALYTICS_ENABLED";
    private static final String ASSOCIATED_IDENTIFIERS_KEY = KEY_PREFIX + ".ASSOCIATED_IDENTIFIERS";
    private static final String ADVERTISING_ID_AUTO_TRACKING_KEY = KEY_PREFIX + ".ADVERTISING_ID_TRACKING";

    /**
     * Intent action for application foreground.
     */
    public static final String ACTION_APP_FOREGROUND = "com.urbanairship.analytics.APP_FOREGROUND";

    /**
     * Intent action for application background.
     */
    public static final String ACTION_APP_BACKGROUND = "com.urbanairship.analytics.APP_BACKGROUND";

    private static LifeCycleCallbacks lifeCycleCallbacks;

    private final ActivityMonitor activityMonitor;
    private final PreferenceDataStore preferenceDataStore;
    private final Context context;
    private final JobDispatcher jobDispatcher;

    private final int platform;
    private boolean inBackground;

    private final AirshipConfigOptions configOptions;
    private final List<AnalyticsListener> analyticsListeners = new ArrayList<>();
    private String sessionId;
    private String conversionSendId;
    private String conversionMetadata;

    private String currentScreen;
    private String previousScreen;
    private long screenStartTime;

    private final Object associatedIdentifiersLock = new Object();

    private AnalyticsJobHandler analyticsJobHandler;

    /**
     * The Analytics constructor, used by {@link com.urbanairship.UAirship}.  You should not instantiate this class directly.
     *
     * @hide
     */
    public Analytics(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore, @NonNull AirshipConfigOptions options, int platform) {
        this(context, preferenceDataStore, options, platform, JobDispatcher.shared(context), new ActivityMonitor());
    }

    /**
     * The Analytics constructor.
     * @param context The application context.
     * @param preferenceDataStore The preference data store.
     * @param options The airship config options.
     * @param platform The device platform.
     * @param jobDispatcher The job dispatcher.
     * @param activityMonitor Optional activityMonitor.
     */
    @VisibleForTesting
    Analytics(@NonNull final Context context, @NonNull PreferenceDataStore preferenceDataStore,
              @NonNull AirshipConfigOptions options, int platform, @NonNull JobDispatcher jobDispatcher,
              @NonNull ActivityMonitor activityMonitor) {

        this.context = context.getApplicationContext();
        this.preferenceDataStore = preferenceDataStore;
        this.inBackground = true; //application is starting
        this.configOptions = options;
        this.activityMonitor = activityMonitor;
        this.platform = platform;
        this.jobDispatcher = jobDispatcher;
    }

    @Override
    protected void init() {
        startNewSession();

        this.activityMonitor.setListener(new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long timeMS) {
                // Start a new environment when the app enters the foreground
                startNewSession();

                inBackground = false;

                // If the app backgrounded, there should be no current screen
                if (currentScreen == null) {
                    trackScreen(previousScreen);
                }

                // If advertising ID tracking is enabled, dispatch a job to update the advertising ID.
                if (isAutoTrackAdvertisingIdEnabled()) {
                    jobDispatcher.dispatch(Job.newBuilder(AnalyticsJobHandler.ACTION_UPDATE_ADVERTISING_ID)
                                              .setAirshipComponent(Analytics.class)
                                              .build());
                }

                // Send the foreground broadcast
                LocalBroadcastManager.getInstance(context)
                                     .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

                addEvent(new AppForegroundEvent(timeMS));
            }

            @Override
            public void onBackground(long timeMS) {
                inBackground = true;

                // Stop tracking screen
                trackScreen(null);

                addEvent(new AppBackgroundEvent(timeMS));

                // Send the background broadcast
                LocalBroadcastManager.getInstance(context)
                                     .sendBroadcast(new Intent(Analytics.ACTION_APP_BACKGROUND));

                setConversionSendId(null);
                setConversionMetadata(null);
            }
        });
    }


    @Override
    protected int onPerformJob(@NonNull UAirship airship, Job job) {
        if (analyticsJobHandler == null) {
            analyticsJobHandler = new AnalyticsJobHandler(context, airship, preferenceDataStore);
        }

        return analyticsJobHandler.performJob(job);
    }

    @Override
    protected void tearDown() {
        activityMonitor.setListener(null);
    }

    /**
     * Call this in your Activity's <code>onStart</code> method to notify Analytics that the activity has started.
     * This is non-blocking and should be called on the application's main thread. If your application
     * targets Android Ice Cream Sandwich (api 14) or above, this method is no longer required.
     *
     * @param activity The activity that is currently starting.
     */
    public static void activityStarted(@NonNull final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return;
        }

        final long timeMS = System.currentTimeMillis();
        UAirship.shared(new UAirship.OnReadyCallback() {
            @Override
            public void onAirshipReady(UAirship airship) {
                airship.getAnalytics().activityMonitor.activityStarted(activity, timeMS);
            }
        });
    }

    /**
     * Call this in your Activity's <code>onStop</code> method to notify Analytics that the activity has stopped.
     * This is non-blocking and should be called on the application's main thread. If your application
     * targets Android Ice Cream Sandwich (api 14) or above, this method is no longer required.
     *
     * @param activity The activity that is currently stopping.
     */
    public static void activityStopped(@NonNull final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return;
        }

        final long timeMS = System.currentTimeMillis();
        UAirship.shared(new UAirship.OnReadyCallback() {
            @Override
            public void onAirshipReady(UAirship airship) {
                airship.getAnalytics().activityMonitor.activityStopped(activity, timeMS);
            }
        });
    }

    /**
     * Determines if the application is in the foreground.
     *
     * @return <code>true</code> if the application is in the foreground, otherwise
     * <code>false</code>.
     */
    public boolean isAppInForeground() {
        return !inBackground;
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

        String eventPayload = event.createEventPayload(sessionId);
        if (eventPayload == null) {
            Logger.error("Analytics - Failed to add event " + event.getType());
        }

        Logger.verbose("Analytics - Adding event: " + event.getType());
        Job addEventJob = Job.newBuilder(AnalyticsJobHandler.ACTION_ADD)
                             .setAirshipComponent(Analytics.class)
                             .putExtra(AnalyticsJobHandler.EXTRA_EVENT_TYPE, event.getType())
                             .putExtra(AnalyticsJobHandler.EXTRA_EVENT_ID, event.getEventId())
                             .putExtra(AnalyticsJobHandler.EXTRA_EVENT_DATA, eventPayload)
                             .putExtra(AnalyticsJobHandler.EXTRA_EVENT_TIME_STAMP, event.getTime())
                             .putExtra(AnalyticsJobHandler.EXTRA_EVENT_SESSION_ID, sessionId)
                             .putExtra(AnalyticsJobHandler.EXTRA_EVENT_PRIORITY, event.getPriority())
                             .build();

        jobDispatcher.dispatch(addEventJob);

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
     * Registers analytics for life cycle callbacks.
     *
     * @param application The application.
     * @hide
     */
    @TargetApi(14)
    public static void registerLifeCycleCallbacks(@NonNull Application application) {
        if (lifeCycleCallbacks == null) {
            lifeCycleCallbacks = new LifeCycleCallbacks(application) {
                @Override
                public void onActivityStarted(final Activity activity) {
                    final long timeStamp = System.currentTimeMillis();
                    UAirship.shared(new UAirship.OnReadyCallback() {
                        @Override
                        public void onAirshipReady(UAirship airship) {
                            airship.getAnalytics().activityMonitor.activityStarted(activity, timeStamp);
                        }
                    });
                }

                @Override
                public void onActivityStopped(final Activity activity) {
                    final long timeStamp = System.currentTimeMillis();
                    UAirship.shared(new UAirship.OnReadyCallback() {
                        @Override
                        public void onAirshipReady(UAirship airship) {
                            airship.getAnalytics().activityMonitor.activityStopped(activity, timeStamp);
                        }
                    });
                }
            };

            lifeCycleCallbacks.register();
        }
    }

    /**
     * Unregisters analytics for life cycle callbacks.
     *
     * @hide
     */
    @TargetApi(14)
    public static void unregisterLifeCycleCallbacks() {
        if (lifeCycleCallbacks != null) {
            lifeCycleCallbacks.unregister();
        }
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
            jobDispatcher.dispatch(Job.newBuilder(AnalyticsJobHandler.ACTION_DELETE_ALL)
                                      .setAirshipComponent(Analytics.class)
                                      .build());
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
            jobDispatcher.dispatch(Job.newBuilder(AnalyticsJobHandler.ACTION_UPDATE_ADVERTISING_ID)
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
     * Associates identifiers with the device. This will create and add an event
     * that will be sent up with other analytics events. Previous
     * associated identifiers will be replaced.
     *
     * @param identifiers An {@link AssociatedIdentifiers} instance.
     * @deprecated Marked to be removed in 8.0.0. Use editAssociatedIdentifiers() instead.
     */
    @Deprecated
    public void associateIdentifiers(@NonNull AssociatedIdentifiers identifiers) {
        synchronized (associatedIdentifiersLock) {
            preferenceDataStore.put(ASSOCIATED_IDENTIFIERS_KEY, identifiers);
            addEvent(new AssociateIdentifiersEvent(identifiers));
        }
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
        jobDispatcher.dispatch(Job.newBuilder(AnalyticsJobHandler.ACTION_SEND)
                                  .setAirshipComponent(Analytics.class)
                                  .build());
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
    public boolean removeAnalyticsListener(AnalyticsListener analyticsListener) {
        synchronized (analyticsListeners) {
            return analyticsListeners.remove(analyticsListener);
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
                    listener.onCustomEventAdded((CustomEvent) event);
                    break;
                case RegionEvent.TYPE:
                    listener.onRegionEventAdded((RegionEvent) event);
                    break;
                default:
                    break;
            }
        }
    }
}
