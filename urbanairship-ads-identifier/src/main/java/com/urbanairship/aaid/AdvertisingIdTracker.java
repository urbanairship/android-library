/* Copyright Airship and Contributors */

package com.urbanairship.aaid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AssociatedIdentifiers;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;

/**
 * Helper class that auto tracks the android advertising Id. The ID will
 * automatically be set on {@link Analytics} by editing the associated identifiers
 * using {@link Analytics#editAssociatedIdentifiers()}.
 */
public class AdvertisingIdTracker extends AirshipComponent {

    private static final String ENABLED_KEY = "com.urbanairship.analytics.ADVERTISING_ID_TRACKING";

    // Using application context
    @SuppressLint("StaticFieldLeak")
    private static AdvertisingIdTracker sharedInstance;

    private UAirship airship;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AdvertisingIdTracker(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        super(context, dataStore);
    }

    @Override
    protected void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
        this.airship = airship;
        update();
    }

    /**
     * Gets the the tracker.
     *
     * @return The tracker.
     */
    @NonNull
    public static AdvertisingIdTracker shared() {
        if (sharedInstance == null) {
            sharedInstance = (AdvertisingIdTracker) UAirship.shared().getComponent(AdvertisingIdTracker.class);
        }

        if (sharedInstance == null) {
            throw new IllegalStateException("Takeoff must be called");
        }

        return sharedInstance;
    }

    @Override
    protected void init() {
        super.init();
        GlobalActivityMonitor.shared(getContext()).addApplicationListener(new SimpleApplicationListener() {
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
        return getDataStore().getBoolean(ENABLED_KEY, false);
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
            getDataStore().put(ENABLED_KEY, isEnabled);
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
            getDataStore().remove(ENABLED_KEY);
            airship.getAnalytics()
                   .editAssociatedIdentifiers()
                   .removeAdvertisingId()
                   .apply();
        }
    }

    private void update() {
        final UAirship airship = this.airship;
        if (airship == null) {
            return;
        }

        UpdateIdTask task = new UpdateIdTask(getContext(), airship.getPlatformType(), new UpdateIdTask.Callback() {
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
                Logger.error(e, "AdvertisingIdTracker - Failed to retrieve and update advertising ID.");
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
