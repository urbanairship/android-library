/* Copyright Airship and Contributors */

package com.urbanairship.aaid;

import android.content.Context;
import android.provider.Settings;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AssociatedIdentifiers;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Helper class that auto tracks the android advertising Id. The ID will
 * automatically be set on {@link Analytics} by editing the associated identifiers
 * using {@link Analytics#editAssociatedIdentifiers()}.
 */
public class AdvertisingIdTracker extends AirshipComponent {

    private final Executor EXECUTOR = AirshipExecutors.newSerialExecutor();

    private static final String ENABLED_KEY = "com.urbanairship.analytics.ADVERTISING_ID_TRACKING";

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
        return UAirship.shared().requireComponent(AdvertisingIdTracker.class);
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

            if (!isDataCollectionEnabled()) {
                Logger.warn("AdvertisingIdTracker - Unable to track advertising ID when opted out of data collection.");
                return;
            }

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

    @Override
    protected void onDataCollectionEnabledChanged(boolean isDataCollectionEnabled) {
        if (isEnabled() && isDataCollectionEnabled) {
            update();
        }
    }

    private void update() {
        final UAirship airship = this.airship;
        if (airship == null) {
            return;
        }

        if (!isEnabled() || !isDataCollectionEnabled()) {
            return;
        }

        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                String advertisingId = null;
                boolean limitedAdTrackingEnabled = true;
                Context context = getContext();

                switch (airship.getPlatformType()) {
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
                            Logger.error(e, "AdvertisingIdTracker - Failed to retrieve and update advertising ID.");
                            return;
                        }

                        break;
                }

                if (!isEnabled() || !isDataCollectionEnabled()) {
                    return;
                }

                AssociatedIdentifiers associatedIdentifiers = airship.getAnalytics()
                                                                     .getAssociatedIdentifiers();

                if (advertisingId != null && (!UAStringUtil.equals(associatedIdentifiers.getAdvertisingId(), advertisingId) ||
                        associatedIdentifiers.isLimitAdTrackingEnabled() != limitedAdTrackingEnabled)) {

                    synchronized (AdvertisingIdTracker.this) {
                        if (!isEnabled()) {
                            return;
                        }

                        airship.getAnalytics()
                               .editAssociatedIdentifiers()
                               .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
                               .apply();
                    }
                }
            }
        });
    }

}
