/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;

import com.urbanairship.analytics.Analytics;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleApplicationListener;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * ApplicationMetrics stores metric information about the application.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ApplicationMetrics extends AirshipComponent {

    private static final String LAST_OPEN_KEY = "com.urbanairship.application.metrics.LAST_OPEN";
    private static final String LAST_APP_VERSION_KEY = "com.urbanairship.application.metrics.APP_VERSION";

    private final ApplicationListener listener;
    private final ActivityMonitor activityMonitor;
    private final PrivacyManager privacyManager;

    private boolean appVersionUpdated;

    ApplicationMetrics(@NonNull Context context, @NonNull final PreferenceDataStore preferenceDataStore, @NonNull PrivacyManager privacyManager) {
        this(context, preferenceDataStore, privacyManager, GlobalActivityMonitor.shared(context));
    }

    ApplicationMetrics(@NonNull Context context, @NonNull final PreferenceDataStore preferenceDataStore,
                       @NonNull final PrivacyManager privacyManager, @NonNull ActivityMonitor activityMonitor) {
        super(context, preferenceDataStore);
        this.activityMonitor = activityMonitor;
        this.privacyManager = privacyManager;
        this.listener = new SimpleApplicationListener() {
            @Override
            public void onForeground(long time) {
                if (privacyManager.isAnyEnabled(PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_IN_APP_AUTOMATION)) {
                    getDataStore().put(LAST_OPEN_KEY, time);
                }
            }
        };
        this.appVersionUpdated = false;
    }

        @Override
    protected void init() {
        super.init();

        updateData();
        privacyManager.addListener(new PrivacyManager.Listener() {
            @Override
            public void onEnabledFeaturesChanged() {
                updateData();
            }
        });
        activityMonitor.addApplicationListener(listener);
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeApplicationListener(listener);
    }

    /**
     * Gets the time of the last open in milliseconds since
     * January 1, 1970 00:00:00.0 UTC.
     *
     * Requires {@link PrivacyManager#FEATURE_IN_APP_AUTOMATION} or {@link PrivacyManager#FEATURE_ANALYTICS} to be enabled.
     *
     * <p>
     * An application "open" is determined in {@link com.urbanairship.analytics.Analytics}
     * by tracking activity start and stops.  This ensures that background services or
     * broadcast receivers do not affect this number.  This number could be inaccurate
     * if analytic instrumentation is missing for activities when running on Android
     * ICS (4.0) or older.
     *
     * @return The time in milliseconds of the last application open, or -1 if the
     * last open has not been detected yet.
     *
     * @deprecated Will be removed in SDK 15.
     */
    @Deprecated
    public long getLastOpenTimeMillis() {
        return getDataStore().getLong(LAST_OPEN_KEY, -1);
    }

    /**
     * Determines whether the app version has been updated.
     *
     * Requires {@link PrivacyManager#FEATURE_IN_APP_AUTOMATION} or {@link PrivacyManager#FEATURE_ANALYTICS} to be enabled.
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
    public long getCurrentAppVersion() {
        return UAirship.getAppVersion();
    }

    private long getLastAppVersion() {
        return getDataStore().getLong(LAST_APP_VERSION_KEY, -1);
    }

    private void updateData() {
        if (privacyManager.isAnyEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION, PrivacyManager.FEATURE_ANALYTICS)) {
            long currentAppVersion = UAirship.getAppVersion();
            long lastAppVersion = getLastAppVersion();

            if (lastAppVersion > -1 && currentAppVersion > lastAppVersion) {
                appVersionUpdated = true;
            }

            getDataStore().put(LAST_APP_VERSION_KEY, currentAppVersion);
        } else {
            getDataStore().remove(LAST_APP_VERSION_KEY);
            getDataStore().remove(LAST_OPEN_KEY);
        }
    }
}
