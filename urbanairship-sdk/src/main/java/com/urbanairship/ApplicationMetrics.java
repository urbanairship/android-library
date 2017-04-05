/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * ApplicationMetrics stores metric information about the application.
 */
public class ApplicationMetrics extends AirshipComponent {

    private static final String LAST_OPEN_KEY = "com.urbanairship.application.metrics.LAST_OPEN";
    private final PreferenceDataStore preferenceDataStore;
    private final Context context;
    private final ActivityMonitor.Listener listener;
    private final ActivityMonitor activityMonitor;

    ApplicationMetrics(@NonNull Context context, @NonNull final PreferenceDataStore preferenceDataStore,
                       @NonNull ActivityMonitor activityMonitor) {
        this.preferenceDataStore = preferenceDataStore;
        this.context = context.getApplicationContext();
        this.listener = new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long time) {
                preferenceDataStore.put(LAST_OPEN_KEY, time);
            }
        };
        this.activityMonitor = activityMonitor;
    }

    @Override
    protected void init() {
        activityMonitor.addListener(listener);
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeListener(listener);
    }

    /**
     * Gets the time of the last open in milliseconds since
     * January 1, 1970 00:00:00.0 UTC.
     * <p/>
     * An application "open" is determined in {@link com.urbanairship.analytics.Analytics}
     * by tracking activity start and stops.  This ensures that background services or
     * broadcast receivers do not affect this number.  This number could be inaccurate
     * if analytic instrumentation is missing for activities when running on Android
     * ICS (4.0) or older.
     *
     * @return The time in milliseconds of the last application open, or -1 if the
     * last open has not been detected yet.
     */
    public long getLastOpenTimeMillis() {
        return preferenceDataStore.getLong(LAST_OPEN_KEY, -1);
    }
}
