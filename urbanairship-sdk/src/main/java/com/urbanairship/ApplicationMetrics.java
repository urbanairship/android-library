/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.analytics.Analytics;

/**
 * ApplicationMetrics stores metric information about the application.
 */
public class ApplicationMetrics extends AirshipComponent {

    private static final String LAST_OPEN_KEY = "com.urbanairship.application.metrics.LAST_OPEN";
    private final PreferenceDataStore preferenceDataStore;
    private final Context context;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            preferenceDataStore.put(LAST_OPEN_KEY, System.currentTimeMillis());
        }
    };

    ApplicationMetrics(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
        this.context = context.getApplicationContext();
    }

    @Override
    protected void init() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Analytics.ACTION_APP_FOREGROUND);

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        broadcastManager.registerReceiver(broadcastReceiver, filter);
    }

    @Override
    protected void tearDown() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver);
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
