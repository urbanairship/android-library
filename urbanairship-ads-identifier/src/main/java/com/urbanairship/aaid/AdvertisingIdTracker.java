/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.aaid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.ActivityMonitor;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AssociatedIdentifiers;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;


/**
 * Helper class that auto tracks the android advertising Id. The ID will
 * automatically be set on {@link Analytics} by editing the associated identifiers
 * using {@link Analytics#editAssociatedIdentifiers()}.
 */
public class AdvertisingIdTracker {

    private static final String PREFERENCE_NAME = "com.urbanairship.aaid.preferences";
    private static final String ENABLED_KEY = "ENABLED";


    // Using application context
    @SuppressLint("StaticFieldLeak")
    private static AdvertisingIdTracker sharedInstance;

    private final Context context;
    private final SharedPreferences preferences;

    private UAirship airship;

    private AdvertisingIdTracker(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the the tracker.
     *
     * @param context The application context.
     * @return The tracker.
     */
    @NonNull
    public static AdvertisingIdTracker shared(@NonNull Context context) {
        if (sharedInstance != null) {
            return sharedInstance;
        }

        synchronized (AdvertisingIdTracker.class) {
            if (sharedInstance == null) {
                sharedInstance = new AdvertisingIdTracker(context);
                sharedInstance.init();
            }
        }

        return sharedInstance;
    }

    private void init() {
        ActivityMonitor.shared(context).addListener(new ActivityMonitor.SimpleListener() {
            @Override
            public void onForeground(long time) {
                update();
            }
        });
    }

    /**
     * Returns {@code true} if the tracker is enabled, {@code false} if its disabled.
     *
     * @return {@code true} if the tracker is enabled, {@code false} if its disabled.
     */
    public boolean isEnabled() {
        return preferences.getBoolean(ENABLED_KEY, false);
    }

    /**
     * Enables or disables the tracker.
     * <p>
     * The value is persisted in shared preferences.
     *
     * @param isEnabled {@code true} to enable the tracker, otherwise {@code false}.
     */
    public void setEnabled(boolean isEnabled) {
        synchronized (this) {
            preferences.edit().putBoolean(ENABLED_KEY, isEnabled).apply();

            if (isEnabled) {
                update();
            }
        }
    }

    /**
     * Disables the tracker and clears the current advertising ID.
     */
    public void clear() {
        synchronized (this) {
            preferences.edit().putBoolean(ENABLED_KEY, false).apply();

            airship.getAnalytics()
                   .editAssociatedIdentifiers()
                   .removeAdvertisingId()
                   .apply();
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void setAirshipInstance(UAirship airship) {
        this.airship = airship;
        update();
    }

    private void update() {
        final UAirship airship = this.airship;
        if (airship == null) {
            return;
        }

        UpdateIdTask task = new UpdateIdTask(context, airship.getPlatformType(), new UpdateIdTask.Callback() {
            @Override
            public void onResult(String advertisingId, boolean isLimitedTrackingEnabled) {
                if (!isEnabled()) {
                    return;
                }

                AssociatedIdentifiers associatedIdentifiers = airship.getAnalytics()
                                                                     .getAssociatedIdentifiers();

                if (advertisingId != null && (!UAStringUtil.equals(associatedIdentifiers.getAdvertisingId(), advertisingId) ||
                        associatedIdentifiers.isLimitAdTrackingEnabled() != isLimitedTrackingEnabled)) {

                    synchronized (AdvertisingIdTracker.this) {
                        if (!isEnabled()) {
                            return;
                        }

                        airship.getAnalytics()
                               .editAssociatedIdentifiers()
                               .setAdvertisingId(advertisingId, isLimitedTrackingEnabled)
                               .apply();
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Logger.error("AdvertisingIdTracker - Failed to retrieve and update advertising ID.", e);
            }
        });

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static class UpdateIdTask extends AsyncTask<Void, Void, Void> {

        // Using application context
        @SuppressLint("StaticFieldLeak")
        private final Context context;
        private final Callback callback;
        private final int platform;

        interface Callback {
            void onResult(@Nullable String advertisingId, boolean isLimitedTrackingEnabled);

            void onError(Exception e);
        }


        private UpdateIdTask(@NonNull Context context, @UAirship.Platform int platform, @NonNull Callback callback) {
            this.context = context.getApplicationContext();
            this.callback = callback;
            this.platform = platform;
        }

        @Override
        protected Void doInBackground(Void... voids) {

            String advertisingId = null;
            boolean limitedAdTrackingEnabled = true;

            switch (platform) {
                case UAirship.AMAZON_PLATFORM:
                    advertisingId = Settings.Secure.getString(context.getContentResolver(), "advertising_id");
                    limitedAdTrackingEnabled = Settings.Secure.getInt(context.getContentResolver(), "limit_ad_tracking", -1) == 0;
                    break;

                case UAirship.ANDROID_PLATFORM:
                    try {
                        AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                        if (adInfo == null) {
                            break;
                        }

                        advertisingId = adInfo.getId();
                        limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
                    } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
                        callback.onError(e);
                        return null;
                    }

                    break;
            }

            callback.onResult(advertisingId, limitedAdTrackingEnabled);
            return null;
        }
    }
}
