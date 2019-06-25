/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import androidx.annotation.NonNull;

import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.SimpleApplicationListener;

/**
 * ApplicationMetrics stores metric information about the application.
 */
public class ApplicationMetrics extends AirshipComponent {

    private static final String LAST_OPEN_KEY = "com.urbanairship.application.metrics.LAST_OPEN";
    private static final String LAST_APP_VERSION_KEY = "com.urbanairship.application.metrics.APP_VERSION";

    private final PreferenceDataStore preferenceDataStore;
    private final ApplicationListener listener;
    private final ActivityMonitor activityMonitor;
    private boolean appVersionUpdated;

    ApplicationMetrics(@NonNull Context context, @NonNull final PreferenceDataStore preferenceDataStore, @NonNull ActivityMonitor activityMonitor) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.listener = new SimpleApplicationListener() {
            @Override
            public void onForeground(long time) {
                preferenceDataStore.put(LAST_OPEN_KEY, time);
            }
        };
        this.activityMonitor = activityMonitor;
        this.appVersionUpdated = false;
    }

    @Override
    protected void init() {
        super.init();
        checkAppVersion();
        activityMonitor.addApplicationListener(listener);
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeApplicationListener(listener);
    }

    /**
     * Gets the time of the last open in milliseconds since
     * January 1, 1970 00:00:00.0 UTC.
     * <p>
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

    /**
     * Determines whether the app version has been updated.
     *
     * @return <code>true</code> if the app version has been updated, otherwise <code>false</code>.
     */
    public boolean getAppVersionUpdated() {
        return appVersionUpdated;
    }

    /**
     * Gets the current app version.
     *
     * @return The current app version.
     */
    public int getCurrentAppVersion() {
        return UAirship.getAppVersion();
    }

    private int getLastAppVersion() {
        return preferenceDataStore.getInt(LAST_APP_VERSION_KEY, -1);
    }

    private void checkAppVersion() {
        if (UAirship.getAppVersion() > getLastAppVersion()) {
            preferenceDataStore.put(LAST_APP_VERSION_KEY, UAirship.getAppVersion());
            appVersionUpdated = true;
        }
    }

}
