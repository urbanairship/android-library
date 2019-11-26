/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.push.PushManager;

import androidx.annotation.NonNull;

/**
 * Accengage module.
 */
public class Accengage extends AirshipComponent {

    private static Accengage sharedInstance;

    private final AirshipChannel airshipChannel;
    private final PushManager pushManager;
    private final Analytics analytics;

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param dataStore The datastore.
     * @param airshipChannel The airship channel.
     * @param pushManager The push manager.
     * @param analytics The analytics instance.
     */
    Accengage(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
              @NonNull AirshipChannel airshipChannel, @NonNull PushManager pushManager,
              @NonNull Analytics analytics) {
        super(context, dataStore);

        this.airshipChannel = airshipChannel;
        this.pushManager = pushManager;
        this.analytics = analytics;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
    }

    /**
     * Gets the shared Accengage instance.
     *
     * @return The shared Accengage instance.
     */
    @NonNull
    public static Accengage shared() {
        if (sharedInstance == null) {
            sharedInstance = (Accengage) UAirship.shared().getComponent(Accengage.class);
        }

        if (sharedInstance == null) {
            throw new IllegalStateException("Takeoff must be called");
        }

        return sharedInstance;
    }

}
