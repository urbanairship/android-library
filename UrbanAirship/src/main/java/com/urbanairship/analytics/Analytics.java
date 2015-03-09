/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.analytics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.LifeCycleCallbacks;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.location.LocationRequestOptions;

import java.util.UUID;

/**
 * This class is the primary interface to the UrbanAirship Analytics API.
 */
public class Analytics {

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
    private final EventDataManager dataManager;
    private final AnalyticsPreferences preferences;
    private boolean inBackground;

    private int minSdkVersion;
    private AirshipConfigOptions configOptions;
    private Context context;
    private String sessionId;
    private String conversionSendId;

    /**
     * The Analytics constructor, used by {@link com.urbanairship.UAirship}.  You should not instantiate this class directly.
     *
     * @hide
     */
    public Analytics(Context context, PreferenceDataStore preferenceDataStore, AirshipConfigOptions options) {
        this(context, preferenceDataStore, options, new ActivityMonitor(options.minSdkVersion, Build.VERSION.SDK_INT, options.analyticsEnabled));
    }

    /**
     * The Analytics constructor
     *
     * @param context The application context.
     * @param options The airship config options
     * @param activityMonitor Optional activityMonitor
     */
    Analytics(final Context context, PreferenceDataStore preferenceDataStore, AirshipConfigOptions options, ActivityMonitor activityMonitor) {
        this.preferences = new AnalyticsPreferences(preferenceDataStore);
        this.context = context.getApplicationContext();

        this.dataManager = new EventDataManager();
        this.minSdkVersion = options.minSdkVersion;
        this.inBackground = true; //application is starting

        this.configOptions = options;

        startNewSession();

        this.activityMonitor = activityMonitor;
        this.activityMonitor.setListener(new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long timeMS) {
                // Start a new environment when the app enters the foreground
                startNewSession();

                inBackground = false;

                // Send the foreground broadcast
                LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

                addEvent(new AppForegroundEvent(timeMS));
            }

            @Override
            public void onBackground(long timeMS) {
                inBackground = true;
                addEvent(new AppBackgroundEvent(timeMS));

                // Send the background broadcast
                LocalBroadcastManager.getInstance(context)
                                     .sendBroadcast(new Intent(Analytics.ACTION_APP_BACKGROUND));

                setConversionSendId(null);
            }
        });
    }

    /**
     * Call this in your Activity's <code>onStart</code> method to notify Analytics that the activity has started.
     * If you are subclassing {@link com.urbanairship.analytics.InstrumentedActivity} or {@link com.urbanairship.analytics.InstrumentedListActivity},
     * this will be done for you automatically. This is non-blocking and should be called on the application's main thread.
     *
     * @param activity The activity that is currently starting.
     */
    public static void activityStarted(final Activity activity) {
        final long timeMS = System.currentTimeMillis();
        UAirship.shared(new UAirship.OnReadyCallback() {
            @Override
            public void onAirshipReady(UAirship airship) {
                airship.getAnalytics().reportActivityStarted(activity, ActivityMonitor.Source.MANUAL_INSTRUMENTATION, timeMS);
            }
        });
    }

    /**
     * Call this in your Activity's <code>onStop</code> method to notify Analytics that the activity has stopped.
     * If you are subclassing {@link com.urbanairship.analytics.InstrumentedActivity} or {@link com.urbanairship.analytics.InstrumentedListActivity},
     * this will be done for you automatically. This is non-blocking and should be called on the application's main thread.
     *
     * @param activity The activity that is currently stopping.
     */
    public static void activityStopped(final Activity activity) {
        final long timeMS = System.currentTimeMillis();
        UAirship.shared(new UAirship.OnReadyCallback() {
            @Override
            public void onAirshipReady(UAirship airship) {
                airship.getAnalytics().reportActivityStopped(activity, ActivityMonitor.Source.MANUAL_INSTRUMENTATION, timeMS);
            }
        });
    }

    /**
     * Report an activity stopped.
     *
     * @param activity The activity that stopped.
     * @param source The instrumentation source.
     * @param timeMS The time when the activity stopped.
     * @hide
     */
    private void reportActivityStopped(Activity activity, ActivityMonitor.Source source, long timeMS) {
        if (minSdkVersion >= 14 && configOptions.analyticsEnabled && ActivityMonitor.Source.MANUAL_INSTRUMENTATION == source) {
            Logger.warn("activityStopped call is no longer necessary starting with SDK 14 - ICE CREAM SANDWICH. Analytics is auto-instrumented for you.");
        }

        activityMonitor.activityStopped(activity, source, timeMS);
    }

    /**
     * Report an activity started.
     *
     * @param activity The activity that started.
     * @param source The instrumentation source
     * @param timeMS The time when the activity started.
     * @hide
     */
    private void reportActivityStarted(Activity activity, ActivityMonitor.Source source, long timeMS) {
        if (minSdkVersion >= 14 && configOptions.analyticsEnabled && ActivityMonitor.Source.MANUAL_INSTRUMENTATION == source) {
            Logger.warn("activityStarted call is no longer necessary starting with SDK 14 - ICE CREAM SANDWICH. Analytics is auto-instrumented for you.");
        }

        activityMonitor.activityStarted(activity, source, timeMS);
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
    public void addEvent(final Event event) {
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

        Intent i = new Intent(context, EventService.class)
                .setAction(EventService.ACTION_ADD)
                .putExtra(EventService.EXTRA_EVENT_TYPE, event.getType())
                .putExtra(EventService.EXTRA_EVENT_ID, event.getEventId())
                .putExtra(EventService.EXTRA_EVENT_DATA, eventPayload)
                .putExtra(EventService.EXTRA_EVENT_TIME_STAMP, event.getTime())
                .putExtra(EventService.EXTRA_EVENT_SESSION_ID, sessionId);

        if (context.startService(i) == null) {
            Logger.warn("Unable to start analytics service. Check that the event service is added to the manifest.");
        } else {
            Logger.debug("Analytics - Added event: " + event.getType() + ": " + eventPayload);
        }
    }

    /**
     * Records a location.
     *
     * @param location The location to record.
     */
    public void recordLocation(Location location) {
        recordLocation(location, null, LocationEvent.UpdateType.SINGLE);
    }

    /**
     * Records a location.
     *
     * @param location The new location.
     * @param options The location request options.
     * @param updateType The update type.
     */
    public void recordLocation(Location location, LocationRequestOptions options, LocationEvent.UpdateType updateType) {
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
    public void setConversionSendId(String sendId) {
        Logger.debug("Analytics - Setting conversion send ID: " + sendId);
        this.conversionSendId = sendId;
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
     * Gets the EventDataManager
     *
     * @return A EventDataManager
     */
    EventDataManager getDataManager() {
        return dataManager;
    }

    /**
     * Gets the analytic preferences
     *
     * @return The analytic preferences
     */
    AnalyticsPreferences getPreferences() {
        return preferences;
    }


    /**
     * Registers analytics for life cycle callbacks.
     *
     * @param application The application.
     * @hide
     */
    @TargetApi(14)
    public static void registerLifeCycleCallbacks(Application application) {
        if (lifeCycleCallbacks == null) {
            lifeCycleCallbacks = new LifeCycleCallbacks(application) {
                @Override
                public void onActivityStarted(final Activity activity) {
                    final long timeStamp = System.currentTimeMillis();
                    UAirship.shared(new UAirship.OnReadyCallback() {
                        @Override
                        public void onAirshipReady(UAirship airship) {
                            airship.getAnalytics().reportActivityStarted(activity, ActivityMonitor.Source.AUTO_INSTRUMENTATION, timeStamp);
                        }
                    });
                }

                @Override
                public void onActivityStopped(final Activity activity) {
                    final long timeStamp = System.currentTimeMillis();
                    UAirship.shared(new UAirship.OnReadyCallback() {
                        @Override
                        public void onAirshipReady(UAirship airship) {
                            airship.getAnalytics().reportActivityStopped(activity, ActivityMonitor.Source.AUTO_INSTRUMENTATION, timeStamp);
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
     * Sets analytics enabled. When disabling, any locally stored events
     * will be deleted.
     *
     * @param enabled {@code true} to enable analytics, {@code false} to disable.
     */
    public void setEnabled(boolean enabled) {
        // When we disable analytics delete all the events
        if (preferences.isAnalyticsEnabled() && !enabled) {
            Intent i = new Intent(context, EventService.class)
                    .setAction(EventService.ACTION_DELETE_ALL);

            context.startService(i);
        }

        preferences.setAnalyticsEnabled(enabled);
    }

    /**
     * Returns {@code true} if analytics is enabled and {@link com.urbanairship.AirshipConfigOptions#analyticsEnabled}
     * is set to {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if analytics is enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return configOptions.analyticsEnabled && preferences.isAnalyticsEnabled();
    }
}
