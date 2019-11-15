/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.os.Build;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.channel.TagEditor;
import com.urbanairship.channel.TagGroupsEditor;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.notifications.AirshipNotificationProvider;
import com.urbanairship.push.notifications.LegacyNotificationFactoryProvider;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.push.notifications.NotificationChannelRegistry;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.annotation.XmlRes;
import androidx.core.app.NotificationManagerCompat;


/**
 * This class is the primary interface for customizing the display and behavior
 * of incoming push notifications.
 */
public class PushManager extends AirshipComponent {

    /**
     * Action sent as a broadcast when a notification is opened.
     * <p>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE_BUNDLE},
     * {@link #EXTRA_NOTIFICATION_BUTTON_ID},
     * {@link #EXTRA_NOTIFICATION_BUTTON_FOREGROUND}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String ACTION_NOTIFICATION_RESPONSE = "com.urbanairship.push.ACTION_NOTIFICATION_RESPONSE";

    /**
     * Action sent as a broadcast when a notification is dismissed.
     * <p>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE_BUNDLE}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String ACTION_NOTIFICATION_DISMISSED = "com.urbanairship.push.ACTION_NOTIFICATION_DISMISSED";

    /**
     * The notification ID extra contains the ID of the notification placed in the
     * <code>NotificationManager</code> by the library.
     * <p>
     * If a <code>Notification</code> was not created, the extra will not be included.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_ID = "com.urbanairship.push.NOTIFICATION_ID";

    /**
     * The notification tag extra contains the tag of the notification placed in the
     * <code>NotificationManager</code> by the library.
     * <p>
     * If a <code>Notification</code> was not created, the extra will not be included.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_TAG = "com.urbanairship.push.NOTIFICATION_TAG";

    /**
     * The push message extra bundle.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_PUSH_MESSAGE_BUNDLE = "com.urbanairship.push.EXTRA_PUSH_MESSAGE_BUNDLE";

    /**
     * The interactive notification action button identifier extra.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_BUTTON_ID = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ID";

    /**
     * The flag indicating if the interactive notification action button is background or foreground.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_BUTTON_FOREGROUND = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_FOREGROUND";

    /**
     * The CONTENT_INTENT extra is an optional intent that the notification builder can
     * supply on the notification. If set, the intent will be pulled from the notification,
     * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_CONTENT_INTENT = "com.urbanairship.push.EXTRA_NOTIFICATION_CONTENT_INTENT";

    /**
     * The DELETE_INTENT extra is an optional intent that the notification builder can
     * supply on the notification. If set, the intent will be pulled from the notification,
     * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_DELETE_INTENT = "com.urbanairship.push.EXTRA_NOTIFICATION_DELETE_INTENT";

    /**
     * The description of the notification action button.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION = "com.urbanairship.push.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION";

    /**
     * The actions payload for the notification action button.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final String EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD";

    private final String UA_NOTIFICATION_BUTTON_GROUP_PREFIX = "ua_";

    /**
     * Key to store the push canonical IDs for push deduping.
     */
    private static final String LAST_CANONICAL_IDS_KEY = "com.urbanairship.push.LAST_CANONICAL_IDS";

    /**
     * Max amount of canonical IDs to store.
     */
    private static final int MAX_CANONICAL_IDS = 10;

    /**
     * Action to display a notification.
     */
    static final String ACTION_DISPLAY_NOTIFICATION = "ACTION_DISPLAY_NOTIFICATION";

    /**
     * Action to update push registration.
     */
    static final String ACTION_UPDATE_PUSH_REGISTRATION = "ACTION_UPDATE_PUSH_REGISTRATION";

    static final ExecutorService PUSH_EXECUTOR = AirshipExecutors.THREAD_POOL_EXECUTOR;

    static final String KEY_PREFIX = "com.urbanairship.push";
    static final String PUSH_ENABLED_KEY = KEY_PREFIX + ".PUSH_ENABLED";
    static final String USER_NOTIFICATIONS_ENABLED_KEY = KEY_PREFIX + ".USER_NOTIFICATIONS_ENABLED";
    static final String PUSH_TOKEN_REGISTRATION_ENABLED_KEY = KEY_PREFIX + ".PUSH_TOKEN_REGISTRATION_ENABLED";

    // As of version 5.0.0
    static final String PUSH_ENABLED_SETTINGS_MIGRATED_KEY = KEY_PREFIX + ".PUSH_ENABLED_SETTINGS_MIGRATED";
    static final String SOUND_ENABLED_KEY = KEY_PREFIX + ".SOUND_ENABLED";
    static final String VIBRATE_ENABLED_KEY = KEY_PREFIX + ".VIBRATE_ENABLED";
    static final String LAST_RECEIVED_METADATA = KEY_PREFIX + ".LAST_RECEIVED_METADATA";

    static final String QUIET_TIME_ENABLED = KEY_PREFIX + ".QUIET_TIME_ENABLED";
    static final String QUIET_TIME_INTERVAL = KEY_PREFIX + ".QUIET_TIME_INTERVAL";

    // As of version 8.0.0
    static final String PUSH_TOKEN_KEY = KEY_PREFIX + ".REGISTRATION_TOKEN_KEY";

    //singleton stuff
    private final Context context;
    private NotificationProvider notificationProvider;
    private final Map<String, NotificationActionButtonGroup> actionGroupMap = new HashMap<>();
    private final PreferenceDataStore preferenceDataStore;
    private final NotificationManagerCompat notificationManagerCompat;

    private final JobDispatcher jobDispatcher;
    private final PushProvider pushProvider;
    private NotificationChannelRegistry notificationChannelRegistry;

    private NotificationListener notificationListener;
    private List<RegistrationListener> registrationListeners = new CopyOnWriteArrayList<>();
    private List<PushTokenListener> pushTokenListeners = new CopyOnWriteArrayList<>();

    private List<PushListener> pushListeners = new CopyOnWriteArrayList<>();

    private final Object uniqueIdLock = new Object();

    private final AirshipChannel airshipChannel;

    /**
     * Creates a PushManager. Normally only one push manager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getPushManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @param configOptions The airship config options.
     * @param pushProvider The push provider.
     * @param airshipChannel The airship channel.
     * @hide
     */
    public PushManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                       @NonNull AirshipConfigOptions configOptions, @Nullable PushProvider pushProvider,
                       @NonNull AirshipChannel airshipChannel) {

        this(context, preferenceDataStore, configOptions, pushProvider,
                airshipChannel, JobDispatcher.shared(context));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    PushManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                @NonNull AirshipConfigOptions configOptions, PushProvider provider,
                @NonNull AirshipChannel airshipChannel, @NonNull JobDispatcher dispatcher) {
        super(context, preferenceDataStore);
        this.context = context;
        this.preferenceDataStore = preferenceDataStore;
        this.pushProvider = provider;
        this.airshipChannel = airshipChannel;
        this.jobDispatcher = dispatcher;
        this.notificationProvider = new AirshipNotificationProvider(context, configOptions);
        this.notificationManagerCompat = NotificationManagerCompat.from(context);
        this.notificationChannelRegistry = new NotificationChannelRegistry(context, configOptions);

        this.actionGroupMap.putAll(ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_buttons));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.actionGroupMap.putAll(ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_button_overrides));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.migratePushEnabledSettings();

        airshipChannel.addChannelListener(new AirshipChannelListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                for (RegistrationListener listener : registrationListeners) {
                    listener.onChannelCreated(channelId);
                }
            }

            @Override
            public void onChannelUpdated(@NonNull String channelId) {
                for (RegistrationListener listener : registrationListeners) {
                    listener.onChannelUpdated(channelId);
                }
            }
        });

        airshipChannel.addChannelRegistrationPayloadExtender(new AirshipChannel.ChannelRegistrationPayloadExtender() {
            @NonNull
            @Override
            public ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder) {
                if (getPushTokenRegistrationEnabled()) {
                    if (getPushToken() == null) {
                        performPushRegistration(false);
                    }
                    builder.setPushAddress(getPushToken());
                }

                return builder.setOptIn(isOptIn())
                              .setBackgroundEnabled(isPushEnabled() && isPushAvailable());
            }
        });

        notificationChannelRegistry.createDeferredNotificationChannels(R.xml.ua_default_channels);

        dispatchUpdatePushTokenJob();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onComponentEnableChange(boolean isEnabled) {
        if (isEnabled) {
            dispatchUpdatePushTokenJob();
        }
    }

    /**
     * @hide
     */
    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        switch (jobInfo.getAction()) {

            case ACTION_UPDATE_PUSH_REGISTRATION:
                return performPushRegistration(true);

            case ACTION_DISPLAY_NOTIFICATION:
                PushMessage message = PushMessage.fromJsonValue(jobInfo.getExtras().opt(PushProviderBridge.EXTRA_PUSH));
                String providerClass = jobInfo.getExtras().opt(PushProviderBridge.EXTRA_PROVIDER_CLASS).getString();

                if (providerClass == null) {
                    return JobInfo.JOB_FINISHED;
                }

                IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(getContext())
                        .setLongRunning(true)
                        .setProcessed(true)
                        .setMessage(message)
                        .setProviderClass(providerClass)
                        .build();

                pushRunnable.run();

                return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_FINISHED;
    }

    /**
     * Enables channel creation if channel creation has been delayed.
     * <p>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when channelCreationDelayEnabled has been
     * set to <code>true</code> in the airship config.
     *
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#enableChannelCreation()} instead.
     */
    @Deprecated
    public void enableChannelCreation() {
        airshipChannel.enableChannelCreation();
    }

    /**
     * Enables or disables push notifications.
     * <p>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether push is enabled.
     */
    public void setPushEnabled(boolean enabled) {
        preferenceDataStore.put(PUSH_ENABLED_KEY, enabled);
        airshipChannel.updateRegistration();
    }

    /**
     * Determines whether push is enabled.
     *
     * @return <code>true</code> if push is enabled, <code>false</code> otherwise.
     * This defaults to true, and must be explicitly set by the app.
     */
    public boolean isPushEnabled() {
        return preferenceDataStore.getBoolean(PUSH_ENABLED_KEY, true);
    }

    /**
     * Update registration.
     *
     * @deprecated Will be removed in SDk 13. There is no reason to ever use this method. It now no-ops.
     */
    @Deprecated
    public void updateRegistration() {
        //no-op
    }

    /**
     * Enables or disables user notifications.
     * <p>
     * User notifications are push notifications that contain an alert message and are
     * intended to be shown to the user.
     * <p>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether user push is enabled.
     */
    public void setUserNotificationsEnabled(boolean enabled) {
        preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, enabled);
        airshipChannel.updateRegistration();
    }

    /**
     * Determines whether user-facing push notifications are enabled.
     *
     * @return <code>true</code> if user push is enabled, <code>false</code> otherwise.
     */
    public boolean getUserNotificationsEnabled() {
        return preferenceDataStore.getBoolean(USER_NOTIFICATIONS_ENABLED_KEY, false);
    }

    /**
     * Sets the notification factory used when push notifications are received.
     * <p>
     * Specify a notification factory here to customize the display
     * of a push notification's Custom Expanded Views in the
     * Android Notification Manager.
     * <p>
     * If <code>null</code>, push notifications will not be displayed by the
     * library.
     *
     * @param factory The notification factory
     * @see com.urbanairship.push.notifications.NotificationFactory
     * @see com.urbanairship.push.notifications.DefaultNotificationFactory
     * @see com.urbanairship.push.notifications.CustomLayoutNotificationFactory
     * @deprecated Use {@link com.urbanairship.push.notifications.NotificationProvider} and {@link #setNotificationProvider(NotificationProvider)}
     * instead. To be removed in SDK 11.
     */
    public void setNotificationFactory(@Nullable NotificationFactory factory) {
        if (factory == null) {
            this.notificationProvider = null;
        } else {
            this.notificationProvider = new LegacyNotificationFactoryProvider(factory);
        }
    }

    /**
     * Sets the notification provider used to build notifications from a push message
     *
     * If <code>null</code>, notification will not be displayed.
     *
     * @param provider The notification provider
     * @see com.urbanairship.push.notifications.NotificationProvider
     * @see com.urbanairship.push.notifications.AirshipNotificationProvider
     * @see com.urbanairship.push.notifications.CustomLayoutNotificationProvider
     */
    public void setNotificationProvider(@Nullable NotificationProvider provider) {
        this.notificationProvider = provider;
    }

    /**
     * Gets the notification provider.
     *
     * @return The notification provider.
     */
    @Nullable
    public NotificationProvider getNotificationProvider() {
        return notificationProvider;
    }

    /**
     * Returns the shared notification channel registry.
     *
     * @return The NotificationChannelRegistry
     */
    @NonNull
    public NotificationChannelRegistry getNotificationChannelRegistry() {
        return notificationChannelRegistry;
    }

    /**
     * Determines whether sound is enabled.
     *
     * @return A boolean indicated whether sound is enabled.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public boolean isSoundEnabled() {
        return preferenceDataStore.getBoolean(SOUND_ENABLED_KEY, true);
    }

    /**
     * Enables or disables sound.
     *
     * @param enabled A boolean indicating whether sound is enabled.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public void setSoundEnabled(boolean enabled) {
        preferenceDataStore.put(SOUND_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether vibration is enabled.
     *
     * @return A boolean indicating whether vibration is enabled.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public boolean isVibrateEnabled() {
        return preferenceDataStore.getBoolean(VIBRATE_ENABLED_KEY, true);
    }

    /**
     * Enables or disables vibration.
     *
     * @param enabled A boolean indicating whether vibration is enabled.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public void setVibrateEnabled(boolean enabled) {
        preferenceDataStore.put(VIBRATE_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether "Quiet Time" is enabled.
     *
     * @return A boolean indicating whether Quiet Time is enabled.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public boolean isQuietTimeEnabled() {
        return preferenceDataStore.getBoolean(QUIET_TIME_ENABLED, false);
    }

    /**
     * Enables or disables quiet time.
     *
     * @param enabled A boolean indicating whether quiet time is enabled.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public void setQuietTimeEnabled(boolean enabled) {
        preferenceDataStore.put(QUIET_TIME_ENABLED, enabled);
    }

    /**
     * Determines whether we are currently in the middle of "Quiet Time".  Returns false if Quiet Time is disabled,
     * and evaluates whether or not the current date/time falls within the Quiet Time interval set by the user.
     *
     * @return A boolean indicating whether it is currently "Quiet Time".
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public boolean isInQuietTime() {
        if (!this.isQuietTimeEnabled()) {
            return false;
        }

        QuietTimeInterval quietTimeInterval;

        try {
            quietTimeInterval = QuietTimeInterval.fromJson(preferenceDataStore.getJsonValue(QUIET_TIME_INTERVAL));
        } catch (JsonException e) {
            Logger.error("Failed to parse quiet time interval");
            return false;
        }

        return quietTimeInterval.isInQuietTime(Calendar.getInstance());
    }

    /**
     * Returns the Quiet Time interval currently set by the user.
     *
     * @return An array of two Date instances, representing the start and end of Quiet Time.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    @Nullable
    public Date[] getQuietTimeInterval() {
        QuietTimeInterval quietTimeInterval;

        try {
            quietTimeInterval = QuietTimeInterval.fromJson(preferenceDataStore.getJsonValue(QUIET_TIME_INTERVAL));
        } catch (JsonException e) {
            Logger.error("Failed to parse quiet time interval");
            return null;
        }

        return quietTimeInterval.getQuietTimeIntervalDateArray();
    }

    /**
     * Sets the Quiet Time interval.
     *
     * @param startTime A Date instance indicating when Quiet Time should start.
     * @param endTime A Date instance indicating when Quiet Time should end.
     * @deprecated Will be removed in SDK 13. This setting does not work on Android O+. Applications
     * are encouraged to use {@link com.urbanairship.push.notifications.NotificationChannelCompat}
     * instead.
     */
    @Deprecated
    public void setQuietTimeInterval(@NonNull Date startTime, @NonNull Date endTime) {
        QuietTimeInterval quietTimeInterval = QuietTimeInterval.newBuilder()
                                                               .setQuietTimeInterval(startTime, endTime)
                                                               .build();
        preferenceDataStore.put(QUIET_TIME_INTERVAL, quietTimeInterval.toJsonValue());
    }

    /**
     * Determines whether the app is capable of receiving push,
     * meaning whether a FCM or ADM push token is present.
     *
     * @return <code>true</code> if push is available, <code>false</code> otherwise.
     */
    public boolean isPushAvailable() {
        return getPushTokenRegistrationEnabled() && !UAStringUtil.isEmpty(getPushToken());
    }

    /**
     * Returns if the application is currently opted in for push.
     *
     * @return <code>true</code> if opted in for push.
     */
    public boolean isOptIn() {
        return isPushEnabled() && isPushAvailable() && areNotificationsOptedIn();
    }

    /**
     * Checks if notifications are enabled for the app and in the push manager.
     *
     * @return {@code true} if notifications are opted in, otherwise {@code false}.
     */
    public boolean areNotificationsOptedIn() {
        return getUserNotificationsEnabled() && notificationManagerCompat.areNotificationsEnabled();
    }

    /**
     * Set tags for the channel and update the server.
     * <p>
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     * <p>
     * To clear the current set of tags, pass an empty set to this method.
     *
     * @param tags A set of tag strings.
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#setTags(Set)} instead.
     */
    @Deprecated
    public void setTags(@NonNull Set<String> tags) {
        airshipChannel.setTags(tags);
    }

    /**
     * Returns the current set of tags.
     * <p>
     * An empty set indicates that no tags are set on this channel.
     *
     * @return The current set of tags.
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#getTags()} instead.
     */
    @Deprecated
    @NonNull
    public Set<String> getTags() {
        return airshipChannel.getTags();
    }

    /**
     * Determines whether tags are enabled on the device.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     * The default value is <code>true</code>.
     *
     * @return <code>true</code> if tags are enabled on the device, <code>false</code> otherwise.
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#getChannelTagRegistrationEnabled()} instead.
     */
    @Deprecated
    public boolean getChannelTagRegistrationEnabled() {
        return airshipChannel.getChannelTagRegistrationEnabled();
    }

    /**
     * Sets whether tags are enabled on the device. The default value is <code>true</code>.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     *
     * @param enabled A boolean indicating whether tags are enabled on the device.
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#setChannelTagRegistrationEnabled(boolean)} instead.
     */
    @Deprecated
    public void setChannelTagRegistrationEnabled(boolean enabled) {
        airshipChannel.setChannelTagRegistrationEnabled(enabled);
    }

    /**
     * Determines whether the push token is sent during channel registration.
     * If {@code false}, the app will not be able to receive push notifications.
     * The default value is {@code true}.
     *
     * @return {@code true} if the push token is sent during channel registration,
     * {@code false} otherwise.
     */
    public boolean getPushTokenRegistrationEnabled() {
        return getDataStore().getBoolean(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true);
    }

    /**
     * Sets whether the push token is sent during channel registration.
     * If {@code false}, the app will not be able to receive push notifications.
     *
     * @param enabled A boolean indicating whether the push token is sent during
     * channel registration.
     */
    public void setPushTokenRegistrationEnabled(boolean enabled) {
        getDataStore().put(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, enabled);
        airshipChannel.updateRegistration();
    }

    /**
     * Returns the send metadata of the last received push.
     *
     * @return The send metadata from the last received push, or null if not found.
     */
    @Nullable
    public String getLastReceivedMetadata() {
        return preferenceDataStore.getString(LAST_RECEIVED_METADATA, null);
    }

    /**
     * Store the send metadata from the last received push.
     *
     * @param sendMetadata The send metadata string.
     */
    void setLastReceivedMetadata(String sendMetadata) {
        preferenceDataStore.put(LAST_RECEIVED_METADATA, sendMetadata);
    }

    /**
     * Sets the notification listener.
     *
     * @param listener The listener.
     */
    public void setNotificationListener(@Nullable NotificationListener listener) {
        this.notificationListener = listener;
    }

    /**
     * Adds a push listener.
     *
     * @param listener The push listener.
     */
    public void addPushListener(@NonNull PushListener listener) {
        pushListeners.add(listener);
    }

    /**
     * Removes a push listener.
     *
     * @param listener The listener.
     */
    public void removePushListener(@NonNull PushListener listener) {
        pushListeners.remove(listener);
    }

    /**
     * Adds a push token listener.
     *
     * @param listener The listener.
     */
    public void addPushTokenListener(@NonNull PushTokenListener listener) {
        pushTokenListeners.add(listener);
    }

    /**
     * Removes a push token listener.
     *
     * @param listener The listener.
     */
    public void removePushTokenListener(@NonNull PushTokenListener listener) {
        pushTokenListeners.remove(listener);
    }

    /**
     * Adds a registration listener.
     *
     * @param listener The listener.
     * @deprecated Use {@link AirshipChannel#addChannelListener(AirshipChannelListener)} and/or
     * {@link #addPushTokenListener(PushTokenListener)} instead. Will be removed in SDK 13.
     */
    @Deprecated
    public void addRegistrationListener(@NonNull RegistrationListener listener) {
        registrationListeners.add(listener);
    }

    /**
     * Removes a registration listener.
     *
     * @param listener The listener.
     */
    @Deprecated
    public void removeRegistrationListener(@NonNull RegistrationListener listener) {
        registrationListeners.remove(listener);
    }

    /**
     * Gets the notification listener.
     *
     * @return The notification listener.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    NotificationListener getNotificationListener() {
        return notificationListener;
    }

    /**
     * Gets the push listeners.
     *
     * @return The push listeners.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    List<PushListener> getPushListeners() {
        return pushListeners;
    }

    /**
     * Edit the channel tag groups.
     *
     * @return A {@link TagGroupsEditor}.
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#editTagGroups()} instead.
     */
    @Deprecated
    @NonNull
    public TagGroupsEditor editTagGroups() {
        return airshipChannel.editTagGroups();
    }

    /**
     * Edits channel Tags.
     *
     * @return A {@link TagEditor}
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#editTags()} instead.
     */
    @Deprecated
    @NonNull
    public TagEditor editTags() {
        return airshipChannel.editTags();
    }

    /**
     * Register a notification action group under the given name.
     * <p>
     * The provided notification builders will automatically add the actions to the
     * notification when a message is received with a group specified under the
     * {@link com.urbanairship.push.PushMessage#EXTRA_INTERACTIVE_TYPE}
     * key.
     *
     * @param id The id of the action group.
     * @param group The notification action group.
     */
    public void addNotificationActionButtonGroup(@NonNull String id, @NonNull NotificationActionButtonGroup group) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            Logger.error("Unable to add any notification button groups that starts with the reserved Airship prefix %s", UA_NOTIFICATION_BUTTON_GROUP_PREFIX);
            return;
        }

        actionGroupMap.put(id, group);
    }

    /**
     * Adds notification action button groups from an xml file.
     * Example entry:
     * <pre>{@code
     * <UrbanAirshipActionButtonGroup id="custom_group">
     *  <UrbanAirshipActionButton
     *      foreground="true"
     *      id="yes"
     *      android:icon="@drawable/ua_ic_notification_button_accept"
     *      android:label="@string/ua_notification_button_yes"/>
     *  <UrbanAirshipActionButton
     *      foreground="false"
     *      id="no"
     *      android:icon="@drawable/ua_ic_notification_button_decline"
     *      android:label="@string/ua_notification_button_no"/>
     * </UrbanAirshipActionButtonGroup> }</pre>
     *
     * @param context The application context.
     * @param resId The xml resource ID.
     */
    public void addNotificationActionButtonGroups(@NonNull Context context, @XmlRes int resId) {
        Map<String, NotificationActionButtonGroup> groups = ActionButtonGroupsParser.fromXml(context, resId);
        for (Map.Entry<String, NotificationActionButtonGroup> entry : groups.entrySet()) {
            addNotificationActionButtonGroup(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes the notification button group under the given name.
     *
     * @param id The id of the button group to remove.
     */
    public void removeNotificationActionButtonGroup(@NonNull String id) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            Logger.error("Unable to remove any reserved Airship actions groups that begin with %s", UA_NOTIFICATION_BUTTON_GROUP_PREFIX);
            return;
        }

        actionGroupMap.remove(id);
    }

    /**
     * Returns the notification action group that is registered under the given name.
     *
     * @param id The id of the action group.
     * @return The notification action group.
     */
    @Nullable
    public NotificationActionButtonGroup getNotificationActionGroup(@Nullable String id) {
        if (id == null) {
            return null;
        }
        return actionGroupMap.get(id);
    }

    /**
     * Get the Channel ID
     *
     * @return A Channel ID string
     * @deprecated Will be removed in SDK 13. Use {@link AirshipChannel#getId()} instead.
     */
    @Deprecated
    @Nullable
    public String getChannelId() {
        return airshipChannel.getId();
    }

    /**
     * Gets the push token.
     *
     * @return The push token.
     * @deprecated Use {@link #getPushToken()} instead.
     */
    @Nullable
    @Deprecated
    public String getRegistrationToken() {
        return getPushToken();
    }

    /**
     * Gets the push token.
     *
     * @return The push token.
     */
    @Nullable
    public String getPushToken() {
        return preferenceDataStore.getString(PUSH_TOKEN_KEY, null);
    }

    /**
     * Migrates the old push enabled setting to the new user notifications enabled
     * setting, and enables push by default. This was introduced in version 5.0.0.
     */
    void migratePushEnabledSettings() {
        if (preferenceDataStore.getBoolean(PUSH_ENABLED_SETTINGS_MIGRATED_KEY, false)) {
            return;
        }

        Logger.debug("Migrating push enabled preferences");

        // get old push enabled value, defaulting to false as before
        boolean oldPushEnabled = this.preferenceDataStore.getBoolean(PUSH_ENABLED_KEY, false);

        // copy old push enabled value to user notifications enabled slot
        Logger.debug("Setting user notifications enabled to %s", Boolean.toString(oldPushEnabled));
        preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, oldPushEnabled);

        if (!oldPushEnabled) {
            Logger.info("Push is now enabled. You can continue to toggle the opt-in state by enabling or disabling user notifications");
        }

        // set push enabled to true
        preferenceDataStore.put(PUSH_ENABLED_KEY, true);
        preferenceDataStore.put(PUSH_ENABLED_SETTINGS_MIGRATED_KEY, true);
    }

    /**
     * Gets the push provider.
     *
     * @return The available push provider.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PushProvider getPushProvider() {
        return pushProvider;
    }

    /**
     * Check to see if we've seen this ID before. If we have,
     * return false. If not, add the ID to our history and return true.
     *
     * @param canonicalId The canonical push ID for an incoming notification.
     * @return <code>false</code> if the ID exists in the history, otherwise <code>true</code>.
     */
    boolean isUniqueCanonicalId(@Nullable String canonicalId) {
        if (UAStringUtil.isEmpty(canonicalId)) {
            return true;
        }

        synchronized (uniqueIdLock) {
            JsonList jsonList = null;
            try {
                jsonList = JsonValue.parseString(preferenceDataStore.getString(LAST_CANONICAL_IDS_KEY, null)).getList();
            } catch (JsonException e) {
                Logger.debug(e, "PushJobHandler - Unable to parse canonical Ids.");
            }

            List<JsonValue> canonicalIds = jsonList == null ? new ArrayList<JsonValue>() : jsonList.getList();

            // Wrap the canonicalId
            JsonValue id = JsonValue.wrap(canonicalId);

            // Check if the list contains the canonicalId
            if (canonicalIds.contains(id)) {
                return false;
            }

            // Add it
            canonicalIds.add(id);
            if (canonicalIds.size() > MAX_CANONICAL_IDS) {
                canonicalIds = canonicalIds.subList(canonicalIds.size() - MAX_CANONICAL_IDS, canonicalIds.size());
            }

            // Store the new list
            preferenceDataStore.put(LAST_CANONICAL_IDS_KEY, JsonValue.wrapOpt(canonicalIds).toString());

            return true;
        }
    }

    private void dispatchUpdatePushTokenJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setId(JobInfo.CHANNEL_UPDATE_PUSH_TOKEN)
                                 .setAirshipComponent(PushManager.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * Performs push registration.
     *
     * @return {@code true} if push registration either succeeded or is not possible on this device. {@code false} if
     * registration failed and should be retried.
     */
    @JobInfo.JobResult
    int performPushRegistration(boolean updateChannelOnChange) {
        String currentToken = getPushToken();

        if (pushProvider == null) {
            Logger.error("Registration failed. Missing push provider.");
            return JobInfo.JOB_FINISHED;
        }

        synchronized (pushProvider) {
            if (!pushProvider.isAvailable(context)) {
                Logger.error("Registration failed. Push provider unavailable: %s", pushProvider);
                return JobInfo.JOB_RETRY;
            }

            String token;
            try {
                token = pushProvider.getRegistrationToken(context);
            } catch (PushProvider.RegistrationException e) {
                Logger.error(e, "Push registration failed.");
                if (e.isRecoverable()) {
                    return JobInfo.JOB_RETRY;
                } else {
                    return JobInfo.JOB_FINISHED;
                }
            }

            if (token != null && !UAStringUtil.equals(token, currentToken)) {
                Logger.info("PushManagerJobHandler - Push registration updated.");

                preferenceDataStore.put(PUSH_TOKEN_KEY, token);

                for (RegistrationListener listener : registrationListeners) {
                    listener.onPushTokenUpdated(token);
                }

                for (PushTokenListener listener : pushTokenListeners) {
                    listener.onPushTokenUpdated(token);
                }

                if (updateChannelOnChange) {
                    airshipChannel.updateRegistration();
                }
            }

            return JobInfo.JOB_FINISHED;
        }
    }
}
