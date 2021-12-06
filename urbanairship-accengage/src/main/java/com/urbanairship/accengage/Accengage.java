/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.accengage.common.persistence.AccengageSettingsLoader;
import com.urbanairship.accengage.notifications.AccengageNotificationProvider;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.OpenExternalUrlAction;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.iam.actions.LandingPageAction;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.InternalNotificationListener;
import com.urbanairship.push.NotificationActionButtonInfo;
import com.urbanairship.push.NotificationInfo;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.notifications.NotificationProvider;

/**
 * Accengage module.
 */
public class Accengage extends AirshipComponent {

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
     * Accengage OptIn settings file
     */
    @VisibleForTesting
    static final String OPTIN_SETTINGS_FILE = "com.ad4screen.sdk.common.OptinArchive";

    /**
     * Accengage isEnabled setting key (Push Opt-in status)
     */
    @VisibleForTesting
    static final String IS_ENABLED_SETTING_KEY = "isEnabled";

    /**
     * Accengage OptinData settings key (OptIn data status)
     */
    @VisibleForTesting
    static final String OPTIN_DATA_KEY = "optinData";

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

    /**
     * Accengage Device Info settings file
     */
    @VisibleForTesting
    static final String DEVICE_INFO_FILE = "com.ad4screen.sdk.common.DeviceInfo";

    /**
     * Accengage Device Id key
     */
    @VisibleForTesting
    static final String DEVICE_ID_KEY = "idfv";

    /**
     * Accengage data opt-in value.
     */
    @VisibleForTesting
    static final String DATA_OPT_IN = "optin_yes";

    /**
     * Accengage data opt-out value.
     */
    @VisibleForTesting
    static final String DATA_OPT_OUT = "optin_no";

    private final PrivacyManager privacyManager;
    private final AirshipChannel airshipChannel;
    private final PushManager pushManager;
    private final AccengageSettingsLoader settingsLoader;

    private NotificationProvider notificationProvider;
    private String accengageDeviceId;

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param configOptions The config options.
     * @param dataStore The datastore.
     * @param privacyManager The privacy manager.
     * @param airshipChannel The airship channel.
     * @param pushManager The push manager.
     */
    Accengage(@NonNull Context context, @NonNull AirshipConfigOptions configOptions,
              @NonNull PreferenceDataStore dataStore, @NonNull PrivacyManager privacyManager,
              @NonNull AirshipChannel airshipChannel, @NonNull PushManager pushManager) {
        this(context, configOptions, dataStore, privacyManager, airshipChannel, pushManager, new AccengageSettingsLoader());
    }

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param configOptions The config options
     * @param privacyManager The privacy manager..
     * @param dataStore The datastore.
     * @param airshipChannel The airship channel.
     * @param pushManager The push manager.
     * @param settingsLoader The settings loader.
     */
    @VisibleForTesting
    Accengage(@NonNull Context context, @NonNull AirshipConfigOptions configOptions,
              @NonNull PreferenceDataStore dataStore, @NonNull PrivacyManager privacyManager,
              @NonNull AirshipChannel airshipChannel,  @NonNull PushManager pushManager,
              @NonNull AccengageSettingsLoader settingsLoader) {
        super(context, dataStore);
        this.privacyManager = privacyManager;
        this.airshipChannel = airshipChannel;
        this.pushManager = pushManager;
        this.settingsLoader = settingsLoader;
        this.notificationProvider = new AccengageNotificationProvider(configOptions);
    }

    @Override
    protected void init() {
        super.init();

        Logger.debug("Accengage Init");

        JsonMap accengageDeviceInfo = settingsLoader.load(getContext(), DEVICE_INFO_FILE);
        accengageDeviceId = accengageDeviceInfo.opt(DEVICE_ID_KEY).getString();


        // Add Accengage Device ID to Channel Registration Payload
        if (accengageDeviceId != null) {
            airshipChannel.addChannelRegistrationPayloadExtender(new AirshipChannel.ChannelRegistrationPayloadExtender() {
                @NonNull
                @Override
                public ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder) {
                    builder.setAccengageDeviceId(accengageDeviceId);
                    return builder;
                }
            });
        }
        pushManager.addInternalNotificationListener(new InternalNotificationListener() {
            @Override
            public void onNotificationResponse(@NonNull NotificationInfo notificationInfo, @Nullable NotificationActionButtonInfo actionButtonInfo) {
                Accengage.this.onNotificationResponse(notificationInfo, actionButtonInfo);
            }
        });
        migrateAccengageSettings();
    }

    /**
     * Gets the shared Accengage instance.
     *
     * @return The shared Accengage instance.
     */
    @NonNull
    public static Accengage shared() {
        return UAirship.shared().requireComponent(Accengage.class);
    }

    /**
     * Migrate the Accengage settings to Airship
     */
    private void migrateAccengageSettings() {
        if (getDataStore().getBoolean(IS_ALREADY_MIGRATED_PREFERENCE_KEY, false)) {
            return;
        }

        JsonMap accengageSettings = this.settingsLoader.load(getContext(), PUSH_SETTINGS_FILE);
        JsonMap accengageOptinSettings = this.settingsLoader.load(getContext(), OPTIN_SETTINGS_FILE);
        final SharedPreferences prefs = getContext().getSharedPreferences(ACCENGAGE_PREFERENCES_FILE, Context.MODE_PRIVATE);
        if (accengageOptinSettings.isEmpty() && accengageOptinSettings.isEmpty() && !prefs.contains(DO_NOT_TRACK_SETTING_KEY)) {
            // nothing to migrate
            return;
        }


        // Migrate Accengage Push Opt-in Setting
        boolean accengagePushOptinStatus = accengageSettings.opt(IS_ENABLED_SETTING_KEY).getBoolean(true);
        Logger.debug("Migrating Accengage Push Opt-in status : " + accengagePushOptinStatus);
        pushManager.setUserNotificationsEnabled(accengagePushOptinStatus);

        // Migrate Accengage Data Opt-in Setting
        String accengageDataOptinStatus = accengageOptinSettings.opt(OPTIN_DATA_KEY).getString();
        if (accengageDataOptinStatus != null) {
            Logger.debug("Migrating Accengage Data Opt-In status : " + accengageDataOptinStatus);

            if (accengageDataOptinStatus.equals(DATA_OPT_IN)) {
                privacyManager.enable(PrivacyManager.FEATURE_ALL);
            } else if (accengageDataOptinStatus.equals(DATA_OPT_OUT)) {
                privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
            }
        }

        // Migrate Accengage Disabled Tracking Setting
        boolean accengageTrackingDisabledStatus = prefs.getBoolean(DO_NOT_TRACK_SETTING_KEY, false);
        Logger.debug("Migrating Accengage Tracking Disabled status : " + accengageTrackingDisabledStatus);
        if (accengageTrackingDisabledStatus) {
            privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        }

        getDataStore().put(IS_ALREADY_MIGRATED_PREFERENCE_KEY, true);
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

    private void onNotificationResponse(@NonNull NotificationInfo notificationInfo, @Nullable NotificationActionButtonInfo actionButtonInfo) {
        if (notificationInfo.getMessage().isAirshipPush() || !notificationInfo.getMessage().isAccengageVisiblePush()) {
            return;
        }

        AccengageMessage message = AccengageMessage.fromAirshipPushMessage(notificationInfo.getMessage());

        AccengagePushButton button = null;
        if (actionButtonInfo != null) {
            button = getActionButton(message, actionButtonInfo);
        }

        String accengageAction;
        String accengageUrl;
        if (button != null) {
            accengageAction = button.getAccengageAction();
            accengageUrl = button.getAccengageUrl();
        } else {
            accengageAction = message.getAccengageAction();
            accengageUrl = message.getAccengageUrl();
        }

        if (TextUtils.isEmpty(accengageUrl)) {
            Logger.debug("Notification URL is empty.");
            return;
        }

        if (accengageAction != null) {
            switch (accengageAction) {
                case AccengageMessage.ACTION_OPEN_URL:
                    ActionRunRequest.createRequest(OpenExternalUrlAction.DEFAULT_REGISTRY_NAME)
                                    .setValue(accengageUrl)
                                    .setSituation(Action.SITUATION_PUSH_OPENED)
                                    .run();
                    break;
                case AccengageMessage.ACTION_TRACK_URL:
                    Logger.info("Accengage - URL tracking not supported %s", accengageUrl);
                    break;
                case AccengageMessage.ACTION_SHOW_WEBVIEW:
                default:
                    ActionRunRequest.createRequest(LandingPageAction.DEFAULT_REGISTRY_NAME)
                                    .setValue(accengageUrl)
                                    .setSituation(Action.SITUATION_PUSH_OPENED)
                                    .run();
                    break;
            }
        } else {
            if (message.getAccengageOpenWithBrowser()) {
                ActionRunRequest.createRequest(OpenExternalUrlAction.DEFAULT_REGISTRY_NAME)
                                .setValue(accengageUrl)
                                .setSituation(Action.SITUATION_PUSH_OPENED)
                                .run();
            } else {
                ActionRunRequest.createRequest(LandingPageAction.DEFAULT_REGISTRY_NAME)
                                .setValue(accengageUrl)
                                .setSituation(Action.SITUATION_PUSH_OPENED)
                                .run();
            }
        }
    }

    @Nullable
    private AccengagePushButton getActionButton(@NonNull AccengageMessage message, @NonNull NotificationActionButtonInfo actionButtonInfo) {
        for (AccengagePushButton button : message.getButtons()) {
            if (actionButtonInfo.getButtonId().equals(button.getId())) {
                return button;
            }
        }

        Logger.error("Unable to lookup Accengage button with ID: %s", actionButtonInfo.getButtonId());
        return null;
    }

}
