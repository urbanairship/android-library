/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.LocationRequestOptions;
import com.urbanairship.location.RegionEvent;
import com.urbanairship.util.Checks;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * This class is the primary interface to the Airship Analytics API.
 */
public class Analytics extends AirshipComponent {

    /**
     * Job action to send an event.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String ACTION_SEND = "ACTION_SEND";

    /**
     * Minimum amount of delay when {@link #uploadEvents()} is called.
     */
    public static final long SCHEDULE_SEND_DELAY_SECONDS = 10;

    private static final String KEY_PREFIX = "com.urbanairship.analytics";
    private static final String ANALYTICS_ENABLED_KEY = KEY_PREFIX + ".ANALYTICS_ENABLED";
    private static final String ASSOCIATED_IDENTIFIERS_KEY = KEY_PREFIX + ".ASSOCIATED_IDENTIFIERS";

    private final PreferenceDataStore preferenceDataStore;
    private final ActivityMonitor activityMonitor;
    private final EventManager eventManager;
    private final ApplicationListener listener;
    private final AirshipConfigOptions configOptions;
    private final AirshipChannel airshipChannel;
    private final Executor executor;
    private final List<AnalyticsListener> analyticsListeners = new ArrayList<>();
    private final List<EventListener> eventListeners = new ArrayList<>();

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
     * SDK Extensions enum
     * @hide
     */
    @StringDef({ EXTENSION_CORDOVA, EXTENSION_FLUTTER, EXTENSION_REACT_NATIVE, EXTENSION_UNITY, EXTENSION_XAMARIN })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExtensionName {}

    /**
     * SDK Extension = Cordova
     * @hide
     */
    @NonNull
    public static final String EXTENSION_CORDOVA = "cordova";

    /**
     * SDK Extension = Flutter
     * @hide
     */
    @NonNull
    public static final String EXTENSION_FLUTTER = "flutter";

    /**
     * SDK Extension = React-Native
     * @hide
     */
    @NonNull
    public static final String EXTENSION_REACT_NATIVE = "react-native";

    /**
     * SDK Extension = Unity
     * @hide
     */
    @NonNull
    public static final String EXTENSION_UNITY = "unity";

    /**
     * SDK Extension = Xamarin
     * @hide
     */
    @NonNull
    public static final String EXTENSION_XAMARIN = "xamarin";

    @NonNull
    private final Map<String, String> sdkExtensions = new HashMap<>();

    /**
     * The Analytics constructor.
     *
     * @param builder The builder instance.
     */
    private Analytics(@NonNull Builder builder) {
        super(builder.context, builder.preferenceDataStore);
        this.preferenceDataStore = builder.preferenceDataStore;
        this.configOptions = builder.configOptions;
        this.activityMonitor = builder.activityMonitor;
        this.eventManager = builder.eventManager;
        this.airshipChannel = builder.airshipChannel;
        this.executor = builder.executor == null ? AirshipExecutors.newSerialExecutor() : builder.executor;
        this.sessionId = UUID.randomUUID().toString();

        this.listener = new ApplicationListener() {
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

    /**
     * Listener for all Airship events.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface EventListener {

        void onEventAdded(@NonNull Event event, @NonNull String sessionId);

    }

    /**
     * Adds an event listener.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addEventListener(@NonNull EventListener eventListener) {
        synchronized (eventListeners) {
            eventListeners.add(eventListener);
        }
    }

    /**
     * Removes an event listener.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void removeEventListener(@NonNull EventListener eventListener) {
        synchronized (eventListeners) {
            eventListeners.remove(eventListener);
        }
    }

    @Override
    protected void init() {
        super.init();

        activityMonitor.addApplicationListener(listener);

        if (activityMonitor.isAppForegrounded()) {
            onForeground(System.currentTimeMillis());
        }

        airshipChannel.addChannelListener(new AirshipChannelListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                uploadEvents();
            }

            @Override
            public void onChannelUpdated(@NonNull String channelId) {

            }
        });


    }

    @Override
    protected void tearDown() {
        activityMonitor.removeApplicationListener(listener);
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @JobInfo.JobResult
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (analyticsJobHandler == null) {
            analyticsJobHandler = new AnalyticsJobHandler(airship, eventManager);
        }

        return analyticsJobHandler.performJob(jobInfo);
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
            Logger.error("Analytics - Invalid event: %s", event);
            return;
        }

        if (!isEnabled()) {
            Logger.debug("Analytics disabled - ignoring event: %s", event.getType());
            return;
        }

        Logger.verbose("Analytics - Adding event: %s", event.getType());

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
     */
    @Nullable
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
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void setConversionSendId(@Nullable String sendId) {
        Logger.debug("Analytics - Setting conversion send ID: %s", sendId);
        this.conversionSendId = sendId;
    }

    /**
     * Returns the last stored send metadata from when a push conversion was detected.
     *
     * @return A metadata String.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
        Logger.debug("Analytics - Setting conversion metadata: %s", metadata);
        this.conversionMetadata = metadata;
    }

    /**
     * Gets the current environment Id.
     *
     * @return A environment Id String.
     */
    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Called when the app is foregrounded.
     *
     * @param timeMS Time of foregrounding.
     */
    void onForeground(long timeMS) {
        // Start a new environment when the app enters the foreground
        sessionId = UUID.randomUUID().toString();
        Logger.debug("Analytics - New session: %s", sessionId);

        // If the app backgrounded, there should be no current screen
        if (currentScreen == null) {
            trackScreen(previousScreen);
        }

        addEvent(new AppForegroundEvent(timeMS));
    }

    /**
     * Called when the app is backgrounded.
     *
     * @param timeMS Time when backgrounded.
     */
    void onBackground(long timeMS) {
        // Stop tracking screen
        trackScreen(null);

        addEvent(new AppBackgroundEvent(timeMS));
        setConversionSendId(null);
        setConversionMetadata(null);
    }

    /**
     * Sets analytics enabled. When disabling analytics, any locally stored events will be deleted.
     * <p>
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
     * <p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @return {@code true} if analytics is enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return configOptions.analyticsEnabled && preferenceDataStore.getBoolean(ANALYTICS_ENABLED_KEY, true);
    }

    /**
     * Edits the currently stored associated identifiers. All changes made in the editor are batched,
     * and not stored until you call apply(). Calling apply() on the editor will associate the
     * identifiers with the device and add an event that will be sent up with other analytics
     * events. See {@link com.urbanairship.analytics.AssociatedIdentifiers.Editor}
     *
     * @return The AssociatedIdentifiers.Editor
     */
    @NonNull
    public AssociatedIdentifiers.Editor editAssociatedIdentifiers() {
        return new AssociatedIdentifiers.Editor() {
            @Override
            void onApply(boolean clear, @NonNull Map<String, String> idsToAdd, @NonNull List<String> idsToRemove) {
                synchronized (associatedIdentifiersLock) {
                    Map<String, String> ids = new HashMap<>();
                    AssociatedIdentifiers associatedIdentifiers = getAssociatedIdentifiers();

                    if (!clear) {
                        Map<String, String> currentIds = associatedIdentifiers.getIds();
                        ids.putAll(currentIds);
                    }

                    ids.putAll(idsToAdd);

                    for (String key : idsToRemove) {
                        ids.remove(key);
                    }

                    AssociatedIdentifiers identifiers = new AssociatedIdentifiers(ids);
                    AssociatedIdentifiers prev = associatedIdentifiers;

                    if (prev.getIds().equals(identifiers.getIds())) {
                        Logger.info("Skipping analytics event addition for duplicate associated identifiers.");
                        return;
                    }

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
    @NonNull
    public AssociatedIdentifiers getAssociatedIdentifiers() {
        synchronized (associatedIdentifiersLock) {
            try {
                JsonValue value = preferenceDataStore.getJsonValue(ASSOCIATED_IDENTIFIERS_KEY);
                if (!value.isNull()) {
                    return AssociatedIdentifiers.fromJson(value);
                }
            } catch (JsonException e) {
                Logger.error(e, "Unable to parse associated identifiers.");
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
    public void addAnalyticsListener(@NonNull AnalyticsListener analyticsListener) {
        synchronized (analyticsListeners) {
            analyticsListeners.add(analyticsListener);
        }
    }

    /**
     * Removes an {@link AnalyticsListener} for analytics events.
     *
     * @param analyticsListener The {@link AnalyticsListener}.
     */
    public void removeAnalyticsListener(@NonNull AnalyticsListener analyticsListener) {
        synchronized (analyticsListeners) {
            analyticsListeners.remove(analyticsListener);
        }
    }

    /**
     * Applies the set {@link AnalyticsListener} instances to an event.
     *
     * @param event The event.
     */
    private void applyListeners(@NonNull Event event) {
        for (EventListener listener : new ArrayList<>(eventListeners)) {
            listener.onEventAdded(event, getSessionId());
        }

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
     * Registers an SDK extension with the analytics module.
     *
     * @param extension The name of the SDK extension. The compiler will warn if the name is invalid.
     * @param version The version of the SDK extension. Commas will be removed from the string.
     * @hide
     */
    public void registerSDKExtension(@ExtensionName @NonNull String extension, @NonNull String version) {
        // normalize extension
        extension = extension.toLowerCase();

        // normalize version
        version = version.replace(",","");

        sdkExtensions.put(extension, version);
    }

    /**
     * The registered SDK extensions.
     *
     * @return The SDK extensions as a map of extension name to extension version
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public Map<String, String> getExtensions() {
        return sdkExtensions;
    }

    /**
     * Builder factory method.
     *
     * @param context The application context.
     * @return A new builder instance.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static Builder newBuilder(@NonNull Context context) {
        return new Builder(context);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static class Builder {

        private PreferenceDataStore preferenceDataStore;
        private final Context context;
        private ActivityMonitor activityMonitor;
        private EventManager eventManager;
        private AirshipConfigOptions configOptions;
        public AirshipChannel airshipChannel;
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
        @NonNull
        public Builder setPreferenceDataStore(@NonNull PreferenceDataStore preferenceDataStore) {
            this.preferenceDataStore = preferenceDataStore;
            return this;
        }

        /**
         * Sets the {@link ActivityMonitor}.
         *
         * @param activityMonitor The {@link ActivityMonitor}.
         * @return The builder instance.
         */
        @NonNull
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
        @NonNull
        public Builder setEventManager(@NonNull EventManager eventManager) {
            this.eventManager = eventManager;
            return this;
        }

        /**
         * Sets the {@link AirshipConfigOptions}.
         *
         * @param configOptions The {@link AirshipConfigOptions}.
         * @return The builder instance.
         */
        @NonNull
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
        @NonNull
        public Builder setExecutor(@NonNull Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Sets the Airship channel.
         *
         * @param airshipChannel The Airship channel.
         * @return The builder instance.
         */
        @NonNull
        public Builder setAirshipChannel(@NonNull AirshipChannel airshipChannel) {
            this.airshipChannel = airshipChannel;
            return this;
        }

        /**
         * Builds the analytics instance.
         *
         * @return The analytics instance.
         */
        @NonNull
        public Analytics build() {
            Checks.checkNotNull(context, "Missing context.");
            Checks.checkNotNull(activityMonitor, "Missing activity monitor.");
            Checks.checkNotNull(eventManager, "Missing event manager.");
            Checks.checkNotNull(configOptions, "Missing config options.");
            Checks.checkNotNull(airshipChannel, "Missing Airship channel.");
            return new Analytics(this);
        }
    }
}
