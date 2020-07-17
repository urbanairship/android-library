/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.analytics.location.RegionEvent;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;

/**
 * This class is the primary interface to the Airship Analytics API.
 */
public class Analytics extends AirshipComponent {

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
     * Delegate to add analytics headers.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface AnalyticsHeaderDelegate {

        @NonNull
        Map<String, String> onCreateAnalyticsHeaders();

    }

    /**
     * SDK Extensions enum
     *
     * @hide
     */
    @StringDef({ EXTENSION_CORDOVA, EXTENSION_FLUTTER, EXTENSION_REACT_NATIVE, EXTENSION_UNITY, EXTENSION_XAMARIN, EXTENSION_TITANIUM })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExtensionName {}

    /**
     * SDK Extension = Cordova
     *
     * @hide
     */
    @NonNull
    public static final String EXTENSION_CORDOVA = "cordova";

    /**
     * SDK Extension = Flutter
     *
     * @hide
     */
    @NonNull
    public static final String EXTENSION_FLUTTER = "flutter";

    /**
     * SDK Extension = React-Native
     *
     * @hide
     */
    @NonNull
    public static final String EXTENSION_REACT_NATIVE = "react-native";

    /**
     * SDK Extension = Unity
     *
     * @hide
     */
    @NonNull
    public static final String EXTENSION_UNITY = "unity";

    /**
     * SDK Extension = Xamarin
     *
     * @hide
     */
    @NonNull
    public static final String EXTENSION_XAMARIN = "xamarin";

    /**
     * SDK Extension = Titanium
     *
     * @hide
     */
    @NonNull
    public static final String EXTENSION_TITANIUM = "titanum";


    /**
     * Minimum amount of delay when {@link #uploadEvents()} is called.
     */
    private static final long SCHEDULE_SEND_DELAY_SECONDS = 10;

    private static final String KEY_PREFIX = "com.urbanairship.analytics";
    private static final String ANALYTICS_ENABLED_KEY = KEY_PREFIX + ".ANALYTICS_ENABLED";
    private static final String ASSOCIATED_IDENTIFIERS_KEY = KEY_PREFIX + ".ASSOCIATED_IDENTIFIERS";

    private final ActivityMonitor activityMonitor;
    private final EventManager eventManager;
    private final ApplicationListener listener;
    private final AirshipRuntimeConfig runtimeConfig;
    private final AirshipChannel airshipChannel;
    private final Executor executor;
    private final LocaleManager localeManager;
    private final List<AnalyticsListener> analyticsListeners = new CopyOnWriteArrayList<>();
    private final List<EventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final List<AnalyticsHeaderDelegate> headerDelegates = new CopyOnWriteArrayList<>();

    private final Object associatedIdentifiersLock = new Object();

    // Session state
    private String sessionId;
    private String conversionSendId;
    private String conversionMetadata;

    // Screen state
    private String currentScreen;
    private String previousScreen;
    private long screenStartTime;

    @NonNull
    private final List<String> sdkExtensions = new ArrayList<>();

    public Analytics(@NonNull Context context,
                     @NonNull PreferenceDataStore dataStore,
                     @NonNull AirshipRuntimeConfig runtimeConfig,
                     @NonNull AirshipChannel channel,
                     @NonNull LocaleManager localeManager) {
        this(context, dataStore, runtimeConfig, channel, GlobalActivityMonitor.shared(context),
                localeManager, AirshipExecutors.newSerialExecutor(),
                new EventManager(context, dataStore, runtimeConfig));
    }

    @VisibleForTesting
    Analytics(@NonNull Context context,
              @NonNull PreferenceDataStore dataStore,
              @NonNull AirshipRuntimeConfig runtimeConfig,
              @NonNull AirshipChannel airshipChannel,
              @NonNull ActivityMonitor activityMonitor,
              @NonNull LocaleManager localeManager,
              @NonNull Executor executor,
              @NonNull EventManager eventManager) {
        super(context, dataStore);
        this.runtimeConfig = runtimeConfig;
        this.airshipChannel = airshipChannel;
        this.activityMonitor = activityMonitor;
        this.localeManager = localeManager;
        this.executor = executor;
        this.eventManager = eventManager;

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
     * Adds an analytic header delegate.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addHeaderDelegate(@NonNull AnalyticsHeaderDelegate headerDelegate) {
        headerDelegates.add(headerDelegate);
    }

    /**
     * Adds an event listener.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addEventListener(@NonNull EventListener eventListener) {
        eventListeners.add(eventListener);
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

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.ANALYTICS;
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
        if (EventManager.ACTION_SEND.equals(jobInfo.getAction())) {
            if (!isEnabled()) {
                return JobInfo.JOB_FINISHED;
            }

            if (airshipChannel.getId() == null) {
                Logger.debug("AnalyticsJobHandler - No channel ID, skipping analytics send.");
                return JobInfo.JOB_FINISHED;
            }

            if (!eventManager.uploadEvents(getAnalyticHeaders())) {
                return JobInfo.JOB_RETRY;
            }

            return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_FINISHED;
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

        if (!isEnabled() || !isDataCollectionEnabled()) {
            Logger.debug("Analytics - Disabled ignoring event: %s", event.getType());
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
        boolean previousValue = getDataStore().getBoolean(ANALYTICS_ENABLED_KEY, true);

        // When we disable analytics delete all the events
        if (previousValue && !enabled) {
            clearPendingEvents();
        }

        if (enabled && !isDataCollectionEnabled()) {
            Logger.warn("Analytics - Analytics is disabled until data collection is opted in.");
        }

        getDataStore().put(ANALYTICS_ENABLED_KEY, enabled);
    }

    private void clearPendingEvents() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Logger.info("Deleting all analytic events.");
                eventManager.deleteEvents();
            }
        });
    }

    @Override
    protected void onDataCollectionEnabledChanged(boolean isDataCollectionEnabled) {
        if (!isDataCollectionEnabled) {
            clearPendingEvents();
            synchronized (associatedIdentifiersLock) {
                getDataStore().remove(ASSOCIATED_IDENTIFIERS_KEY);
            }
        }
    }

    /**
     * Returns {@code true} if analytics is enabled, {@link com.urbanairship.AirshipConfigOptions#analyticsEnabled}
     * is set to {@code true}, and data collection is opted in, otherwise {@code false}.
     * <p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @return {@code true} if analytics is enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return runtimeConfig.getConfigOptions().analyticsEnabled && getDataStore().getBoolean(ANALYTICS_ENABLED_KEY, true) && isDataCollectionEnabled();
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
                    if (!isDataCollectionEnabled()) {
                        Logger.warn("Analytics - Unable to track associated identifiers when data collection is disabled.");
                        return;
                    }

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

                    getDataStore().put(ASSOCIATED_IDENTIFIERS_KEY, identifiers);
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
                JsonValue value = getDataStore().getJsonValue(ASSOCIATED_IDENTIFIERS_KEY);
                if (!value.isNull()) {
                    return AssociatedIdentifiers.fromJson(value);
                }
            } catch (JsonException e) {
                Logger.error(e, "Unable to parse associated identifiers.");
                getDataStore().remove(ASSOCIATED_IDENTIFIERS_KEY);
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
            for (AnalyticsListener listener : analyticsListeners) {
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
        analyticsListeners.add(analyticsListener);
    }

    /**
     * Removes an {@link AnalyticsListener} for analytics events.
     *
     * @param analyticsListener The {@link AnalyticsListener}.
     */
    public void removeAnalyticsListener(@NonNull AnalyticsListener analyticsListener) {
        analyticsListeners.remove(analyticsListener);
    }

    /**
     * Applies the set {@link AnalyticsListener} instances to an event.
     *
     * @param event The event.
     */
    private void applyListeners(@NonNull Event event) {
        for (EventListener listener : eventListeners) {
            listener.onEventAdded(event, getSessionId());
        }

        for (AnalyticsListener listener :analyticsListeners) {
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

    private Map<String, String> getAnalyticHeaders() {
        Map<String, String> headers = new HashMap<>();

        // Delegates
        for (AnalyticsHeaderDelegate delegate : headerDelegates) {
            headers.putAll(delegate.onCreateAnalyticsHeaders());
        }

        // App info
        headers.put("X-UA-Package-Name", getPackageName());
        headers.put("X-UA-Package-Version", getPackageVersion());
        headers.put("X-UA-Android-Version-Code", String.valueOf(Build.VERSION.SDK_INT));

        // Airship info
        headers.put("X-UA-Device-Family", runtimeConfig.getPlatform() == UAirship.AMAZON_PLATFORM ? "amazon" : "android");
        headers.put("X-UA-Lib-Version", UAirship.getVersion());
        headers.put("X-UA-App-Key", runtimeConfig.getConfigOptions().appKey);
        headers.put("X-UA-In-Production", Boolean.toString(runtimeConfig.getConfigOptions().inProduction));

        headers.put("X-UA-Channel-ID", airshipChannel.getId());
        headers.put("X-UA-Push-Address", airshipChannel.getId());

        if (!sdkExtensions.isEmpty()) {
            headers.put("X-UA-Frameworks", UAStringUtil.join(sdkExtensions, ","));
        }

        // Device info
        headers.put("X-UA-Device-Model", Build.MODEL);
        headers.put("X-UA-Timezone", TimeZone.getDefault().getID());

        Locale locale = localeManager.getLocale();
        if (!UAStringUtil.isEmpty(locale.getLanguage())) {
            headers.put("X-UA-Locale-Language", locale.getLanguage());

            if (!UAStringUtil.isEmpty(locale.getCountry())) {
                headers.put("X-UA-Locale-Country", locale.getCountry());
            }

            if (!UAStringUtil.isEmpty(locale.getVariant())) {
                headers.put("X-UA-Locale-Variant", locale.getVariant());
            }
        }

        return headers;
    }

    @Nullable
    private String getPackageName() {
        try {
            return getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Nullable
    private String getPackageVersion() {
        try {
            return getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
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
        version = version.replace(",", "");

        sdkExtensions.add(extension + ":" + version);
    }

}
