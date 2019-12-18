/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;
import android.content.SharedPreferences;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.accengage.common.persistence.AccengageSettingsLoader;
import com.urbanairship.accengage.notifications.AccengageNotificationProvider;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.notifications.NotificationProvider;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Accengage module.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Accengage extends AirshipComponent {

    private static Accengage sharedInstance;

    private final AirshipChannel airshipChannel;
    private final PushManager pushManager;
    private final Analytics analytics;
    private final AccengageSettingsLoader settingsLoader;

    /**
     * Preference key for Accengage settings migration status
     */
    private static final String IS_ALREADY_MIGRATED_PREFERENCE_KEY = "com.urbanairship.accengage.migrated";

    /**
     * Accengage Push settings file
     */
    @VisibleForTesting
    static final String PUSH_SETTINGS_FILE = "com.ad4screen.sdk.service.modules.push.PushNotification";

    /**
     * Accengage isEnabled setting key (Push Opt-in status)
     */
    @VisibleForTesting
    static final String IS_ENABLED_SETTING_KEY = "isEnabled";

    /**
     * Accengage Preferences file
     */
    @VisibleForTesting
    static final String ACCENGAGE_PREFERENCES_FILE = "com.ad4screen.sdk.A4S";

    /**
     * Accengage doNotTrack setting key (Tracking Disabled status)
     */
    @VisibleForTesting
    static final String DO_NOT_TRACK_SETTING_KEY = "com.ad4screen.sdk.A4S.doNotTrack";

    private NotificationProvider notificationProvider;

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
        this(context, dataStore, airshipChannel, pushManager, analytics, new AccengageSettingsLoader());
    }

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param dataStore The datastore.
     * @param airshipChannel The airship channel.
     * @param pushManager The push manager.
     * @param analytics The analytics instance.
     * @param settingsLoader The settings loader.
     */
    @VisibleForTesting
    Accengage(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
              @NonNull AirshipChannel airshipChannel, @NonNull PushManager pushManager,
              @NonNull Analytics analytics, @NonNull AccengageSettingsLoader settingsLoader) {
        super(context, dataStore);

        this.airshipChannel = airshipChannel;
        this.pushManager = pushManager;
        this.analytics = analytics;
        this.settingsLoader = settingsLoader;
        this.notificationProvider = new AccengageNotificationProvider();
    }

    @Override
    protected void init() {
        super.init();

        if (!getDataStore().getBoolean(IS_ALREADY_MIGRATED_PREFERENCE_KEY, false)) {
            migrateAccengageSettings();
            getDataStore().put(IS_ALREADY_MIGRATED_PREFERENCE_KEY, true);
        }
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

    /**
     * Migrate the Accengage settings to Airship
     */
    private void migrateAccengageSettings() {

        // Migrate Accengage Push Opt-in Setting
        JsonMap accengageSettings = this.settingsLoader.load(getContext(), PUSH_SETTINGS_FILE);
        boolean accengagePushOptinStatus = accengageSettings.opt(IS_ENABLED_SETTING_KEY).getBoolean(true);
        Logger.debug("Accengage - Migrating Accengage Push Opt-in status : " + accengagePushOptinStatus);
        pushManager.setPushEnabled(accengagePushOptinStatus);

        // Migrate Accengage Disabled Tracking Setting
        final SharedPreferences prefs = getContext().getSharedPreferences(ACCENGAGE_PREFERENCES_FILE, Context.MODE_PRIVATE);
        boolean accengageTrackingDisabledStatus = prefs.getBoolean(DO_NOT_TRACK_SETTING_KEY, false);
        Logger.debug("Accengage - Migrating Accengage Tracking Disabled status : " + accengageTrackingDisabledStatus);
        analytics.setEnabled(!accengageTrackingDisabledStatus);
    }

    /**
     * Sets the notification provider used for Accengage messages.
     *
     * @param notificationProvider The notification provider.
     */
    public void setNotificationProvider(@NonNull NotificationProvider notificationProvider) {
        this.notificationProvider = notificationProvider;
    }

    /**
     * Gets the notification provider used for Accengage messages.
     *
     * @return The notification provider.
     */
    @NonNull
    public NotificationProvider getNotificationProvider() {
        return notificationProvider;
    }

}
