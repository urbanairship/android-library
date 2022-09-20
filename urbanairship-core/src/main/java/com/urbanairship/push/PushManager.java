/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.annotation.XmlRes;
import androidx.core.util.ObjectsCompat;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.PushProviders;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionDelegate;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.notifications.AirshipNotificationProvider;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.push.notifications.NotificationChannelRegistry;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

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

    static final ExecutorService PUSH_EXECUTOR = AirshipExecutors.threadPoolExecutor();

    static final String KEY_PREFIX = "com.urbanairship.push";

    static final String USER_NOTIFICATIONS_ENABLED_KEY = KEY_PREFIX + ".USER_NOTIFICATIONS_ENABLED";
    static final String PUSH_DELIVERY_TYPE = KEY_PREFIX + ".PUSH_DELIVERY_TYPE";
    static final String PROVIDER_CLASS_KEY = "com.urbanairship.application.device.PUSH_PROVIDER";

    static final String SOUND_ENABLED_KEY = KEY_PREFIX + ".SOUND_ENABLED";
    static final String VIBRATE_ENABLED_KEY = KEY_PREFIX + ".VIBRATE_ENABLED";
    static final String LAST_RECEIVED_METADATA = KEY_PREFIX + ".LAST_RECEIVED_METADATA";

    static final String QUIET_TIME_ENABLED = KEY_PREFIX + ".QUIET_TIME_ENABLED";
    static final String QUIET_TIME_INTERVAL = KEY_PREFIX + ".QUIET_TIME_INTERVAL";
    static final String PUSH_TOKEN_KEY = KEY_PREFIX + ".REGISTRATION_TOKEN_KEY";

    static final String REQUEST_PERMISSION_KEY = KEY_PREFIX + ".REQUEST_PERMISSION_KEY";

    //singleton stuff
    private final Context context;
    private final Analytics analytics;
    private final AirshipRuntimeConfig config;
    private final Supplier<PushProviders> pushProvidersSupplier;
    private final PermissionsManager permissionsManager;
    private NotificationProvider notificationProvider;
    private final Map<String, NotificationActionButtonGroup> actionGroupMap = new HashMap<>();
    private final PreferenceDataStore preferenceDataStore;
    private final ActivityMonitor activityMonitor;

    private final JobDispatcher jobDispatcher;
    private final NotificationChannelRegistry notificationChannelRegistry;
    private final PrivacyManager privacyManager;
    private final AirshipNotificationManager notificationManager;

    private NotificationListener notificationListener;
    private final List<PushTokenListener> pushTokenListeners = new CopyOnWriteArrayList<>();

    private final List<PushListener> pushListeners = new CopyOnWriteArrayList<>();
    private final List<PushListener> internalPushListeners = new CopyOnWriteArrayList<>();
    private final List<InternalNotificationListener> internalNotificationListeners = new CopyOnWriteArrayList<>();

    private final Object uniqueIdLock = new Object();

    private final AirshipChannel airshipChannel;
    private PushProvider pushProvider;
    private Boolean isPushManagerEnabled;

    private volatile boolean shouldDispatchUpdateTokenJob = true;

    /**
     * Creates a PushManager. Normally only one push manager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getPushManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @param config The airship config options.
     * @param pushProvidersSupplier The push providers supplier.
     * @param airshipChannel The airship channel.
     * @param analytics The analytics instance.
     * @param permissionsManager The permissions manager.
     * @hide
     */
    public PushManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                       @NonNull AirshipRuntimeConfig config, @NonNull PrivacyManager privacyManager,
                       @NonNull Supplier<PushProviders> pushProvidersSupplier, @NonNull AirshipChannel airshipChannel,
                       @NonNull Analytics analytics, @NonNull PermissionsManager permissionsManager) {

        this(context, preferenceDataStore, config, privacyManager, pushProvidersSupplier,
                airshipChannel, analytics, permissionsManager, JobDispatcher.shared(context),
                AirshipNotificationManager.from(context), GlobalActivityMonitor.shared(context));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    PushManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                @NonNull AirshipRuntimeConfig config, @NonNull PrivacyManager privacyManager,
                @NonNull Supplier<PushProviders> pushProvidersSupplier, @NonNull AirshipChannel airshipChannel,
                @NonNull Analytics analytics, @NonNull PermissionsManager permissionsManager,
                @NonNull JobDispatcher dispatcher, @NonNull AirshipNotificationManager notificationManager,
                @NonNull ActivityMonitor activityMonitor) {
        super(context, preferenceDataStore);
        this.context = context;
        this.preferenceDataStore = preferenceDataStore;
        this.config = config;
        this.privacyManager = privacyManager;
        this.pushProvidersSupplier = pushProvidersSupplier;
        this.airshipChannel = airshipChannel;
        this.analytics = analytics;
        this.permissionsManager = permissionsManager;
        this.jobDispatcher = dispatcher;
        this.notificationManager = notificationManager;
        this.activityMonitor = activityMonitor;
        this.notificationProvider = new AirshipNotificationProvider(context, config.getConfigOptions());
        this.notificationChannelRegistry = new NotificationChannelRegistry(context, config.getConfigOptions());

        this.actionGroupMap.putAll(ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_buttons));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.actionGroupMap.putAll(ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_button_overrides));
        }
    }

    @Override
    protected void init() {
        super.init();
        airshipChannel.addChannelRegistrationPayloadExtender(this::extendChannelRegistrationPayload);
        analytics.addHeaderDelegate(this::createAnalyticsHeaders);
        privacyManager.addListener(this::updateManagerEnablement);

        permissionsManager.addAirshipEnabler(permission -> {
            if (permission == Permission.DISPLAY_NOTIFICATIONS) {
                privacyManager.enable(PrivacyManager.FEATURE_PUSH);
                preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, true);
                airshipChannel.updateRegistration();
            }
        });

        permissionsManager.addOnPermissionStatusChangedListener((permission, status) -> {
            if (permission == Permission.DISPLAY_NOTIFICATIONS) {
                airshipChannel.updateRegistration();
            }
        });

        String defaultChannelId = config.getConfigOptions().notificationChannel;
        if (defaultChannelId == null) {
            defaultChannelId = AirshipNotificationProvider.DEFAULT_NOTIFICATION_CHANNEL;
        }

        PermissionDelegate delegate = new NotificationsPermissionDelegate(
                defaultChannelId,
                preferenceDataStore,
                notificationManager,
                notificationChannelRegistry,
                activityMonitor
        );

        activityMonitor.addApplicationListener(new SimpleApplicationListener() {
            @Override
            public void onForeground(long time) {
                checkPermission();
            }
        });

        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, delegate);
        updateManagerEnablement();
    }

    private void checkPermission() {
        checkPermission(null);
    }

    private void checkPermission(@Nullable Runnable onCheckComplete) {
        if (!privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH)) {
            return;
        }

        permissionsManager.checkPermissionStatus(Permission.DISPLAY_NOTIFICATIONS, status -> {
            if (preferenceDataStore.getBoolean(REQUEST_PERMISSION_KEY, true) && activityMonitor.isAppForegrounded() && getUserNotificationsEnabled()) {
                permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, requestResult -> {
                    if (onCheckComplete != null) {
                        onCheckComplete.run();
                    }
                });
                preferenceDataStore.put(REQUEST_PERMISSION_KEY, false);
            } else {
                if (onCheckComplete != null) {
                    onCheckComplete.run();
                }
            }
        });
    }

    private void updateManagerEnablement() {
        if (privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH) && isComponentEnabled()) {
            if (isPushManagerEnabled != null && isPushManagerEnabled) {
                return;
            }

            isPushManagerEnabled = true;
            if (pushProvider == null) {
                pushProvider = resolvePushProvider();
                String pushDeliveryType = preferenceDataStore.getString(PUSH_DELIVERY_TYPE, null);
                if (pushProvider == null || !pushProvider.getDeliveryType().equals(pushDeliveryType)) {
                    clearPushToken();
                }
            }

            if (shouldDispatchUpdateTokenJob) {
                dispatchUpdateJob();
            }

            checkPermission();
        } else {
            if (isPushManagerEnabled != null && !shouldDispatchUpdateTokenJob) {
                return;
            }

            isPushManagerEnabled = false;
            preferenceDataStore.remove(PUSH_DELIVERY_TYPE);
            preferenceDataStore.remove(PUSH_TOKEN_KEY);
            shouldDispatchUpdateTokenJob = true;
        }
    }

    private void dispatchUpdateJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_PUSH_REGISTRATION)
                                 .setAirshipComponent(PushManager.class)
                                 .setConflictStrategy(JobInfo.REPLACE)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    @Nullable
    private PushProvider resolvePushProvider() {
        // Existing provider class
        String existingProviderClass = preferenceDataStore.getString(PROVIDER_CLASS_KEY, null);
        PushProviders pushProviders = ObjectsCompat.requireNonNull(pushProvidersSupplier.get());
        // Try to use the same provider
        if (!UAStringUtil.isEmpty(existingProviderClass)) {
            PushProvider provider = pushProviders.getProvider(config.getPlatform(), existingProviderClass);
            if (provider != null) {
                return provider;
            }
        }

        // Find the best provider for the platform
        PushProvider provider = pushProviders.getBestProvider(config.getPlatform());
        if (provider != null) {
            preferenceDataStore.put(PROVIDER_CLASS_KEY, provider.getClass().toString());
        }

        return provider;
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.PUSH;
    }

    @NonNull
    private ChannelRegistrationPayload.Builder extendChannelRegistrationPayload(@NonNull ChannelRegistrationPayload.Builder builder) {
        if (!isComponentEnabled() || !privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH)) {
            return builder;
        }

        if (getPushToken() == null) {
            performPushRegistration(false);
        }

        String pushToken = getPushToken();
        builder.setPushAddress(pushToken);
        PushProvider provider = getPushProvider();

        if (pushToken != null && provider != null && provider.getPlatform() == UAirship.ANDROID_PLATFORM) {
            builder.setDeliveryType(provider.getDeliveryType());
        }

        return builder.setOptIn(isOptIn())
                      .setBackgroundEnabled(isPushAvailable());
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onComponentEnableChange(boolean isEnabled) {
        updateManagerEnablement();
    }

    /**
     * @hide
     */
    @WorkerThread
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JobResult onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (!privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH)) {
            return JobResult.SUCCESS;
        }

        switch (jobInfo.getAction()) {

            case ACTION_UPDATE_PUSH_REGISTRATION:
                return performPushRegistration(true);

            case ACTION_DISPLAY_NOTIFICATION:
                PushMessage message = PushMessage.fromJsonValue(jobInfo.getExtras().opt(PushProviderBridge.EXTRA_PUSH));
                String providerClass = jobInfo.getExtras().opt(PushProviderBridge.EXTRA_PROVIDER_CLASS).getString();

                if (providerClass == null) {
                    return JobResult.SUCCESS;
                }

                IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(getContext())
                        .setLongRunning(true)
                        .setProcessed(true)
                        .setMessage(message)
                        .setProviderClass(providerClass)
                        .build();

                pushRunnable.run();

                return JobResult.SUCCESS;
        }

        return JobResult.SUCCESS;
    }

    /**
     * Enables or disables push notifications.
     * <p>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether push is enabled.
     * @deprecated Enable/disable by enabling {@link PrivacyManager#FEATURE_PUSH} in {@link PrivacyManager}.
     * This will call through to the privacy manager.
     */
    @Deprecated
    public void setPushEnabled(boolean enabled) {
        if (enabled) {
            privacyManager.enable(PrivacyManager.FEATURE_PUSH);
        } else {
            privacyManager.disable(PrivacyManager.FEATURE_PUSH);
        }
    }

    /**
     * Determines whether push is enabled.
     *
     * @return <code>true</code> if push is enabled, <code>false</code> otherwise.
     * This defaults to true, and must be explicitly set by the app.
     * @deprecated Enable/disable by enabling {@link PrivacyManager#FEATURE_PUSH} in {@link PrivacyManager}.
     * This will call through to the privacy manager.
     */
    @Deprecated
    public boolean isPushEnabled() {
        return privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH);
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
        if (getUserNotificationsEnabled() != enabled) {
            preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, enabled);
            if (enabled) {
                preferenceDataStore.put(REQUEST_PERMISSION_KEY, true);
                checkPermission(airshipChannel::updateRegistration);
            } else {
                airshipChannel.updateRegistration();
            }

        }
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
     * Sets the notification provider used to build notifications from a push message
     * <p>
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
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
     */
    @Deprecated
    public boolean isSoundEnabled() {
        return preferenceDataStore.getBoolean(SOUND_ENABLED_KEY, true);
    }

    /**
     * Enables or disables sound.
     *
     * @param enabled A boolean indicating whether sound is enabled.
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
     */
    @Deprecated
    public void setSoundEnabled(boolean enabled) {
        preferenceDataStore.put(SOUND_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether vibration is enabled.
     *
     * @return A boolean indicating whether vibration is enabled.
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
     */
    @Deprecated
    public boolean isVibrateEnabled() {
        return preferenceDataStore.getBoolean(VIBRATE_ENABLED_KEY, true);
    }

    /**
     * Enables or disables vibration.
     *
     * @param enabled A boolean indicating whether vibration is enabled.
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
     */
    @Deprecated
    public void setVibrateEnabled(boolean enabled) {
        preferenceDataStore.put(VIBRATE_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether "Quiet Time" is enabled.
     *
     * @return A boolean indicating whether Quiet Time is enabled.
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
     */
    @Deprecated
    public boolean isQuietTimeEnabled() {
        return preferenceDataStore.getBoolean(QUIET_TIME_ENABLED, false);
    }

    /**
     * Enables or disables quiet time.
     *
     * @param enabled A boolean indicating whether quiet time is enabled.
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
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
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
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
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
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
     * @deprecated This setting does not work on Android O+. Applications are encouraged to
     * use {@link com.urbanairship.push.notifications.NotificationChannelCompat} instead.
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
        return privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH) && !UAStringUtil.isEmpty(getPushToken());
    }

    /**
     * Returns if the application is currently opted in for push.
     *
     * @return <code>true</code> if opted in for push.
     */
    public boolean isOptIn() {
        return isPushAvailable() && areNotificationsOptedIn();
    }

    /**
     * Checks if notifications are enabled for the app and in the push manager.
     *
     * @return {@code true} if notifications are opted in, otherwise {@code false}.
     */
    public boolean areNotificationsOptedIn() {
        return getUserNotificationsEnabled() && notificationManager.areNotificationsEnabled();
    }

    /**
     * Determines whether the push token is sent during channel registration.
     * If {@code false}, the app will not be able to receive push notifications.
     *
     * @return {@code true} if the push token is sent during channel registration,
     * {@code false} otherwise.
     * @deprecated Check if {@link PrivacyManager#FEATURE_PUSH} is enabled in the {@link PrivacyManager}.
     */
    @Deprecated
    public boolean isPushTokenRegistrationEnabled() {
        return privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH);
    }

    /**
     * Sets whether the push token is sent during channel registration.
     * If {@code false}, the app will not be able to receive push notifications.
     *
     * @param enabled A boolean indicating whether the push token is sent during
     * channel registration.
     * @deprecated Enable/disable {@link PrivacyManager#FEATURE_PUSH} in the {@link PrivacyManager}.
     */
    @Deprecated
    public void setPushTokenRegistrationEnabled(boolean enabled) {
        if (enabled) {
            privacyManager.enable(PrivacyManager.FEATURE_PUSH);
        } else {
            privacyManager.disable(PrivacyManager.FEATURE_PUSH);
        }
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
     * Adds an internal push listener.
     *
     * @param listener The push listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addInternalPushListener(@NonNull PushListener listener) {
        internalPushListeners.add(listener);
    }

    /**
     * Removes a push listener.
     *
     * @param listener The listener.
     */
    public void removePushListener(@NonNull PushListener listener) {
        pushListeners.remove(listener);
        internalPushListeners.remove(listener);
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
     * Adds an internal notification listener.
     *
     * @param listener The notification listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addInternalNotificationListener(@NonNull InternalNotificationListener listener) {
        internalNotificationListeners.add(listener);
    }

    /**
     * Gets the notification listener.
     *
     * @return The notification listener.
     */
    @Nullable
    public NotificationListener getNotificationListener() {
        return notificationListener;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void onPushReceived(@NonNull PushMessage message, boolean notificationPosted) {
        if (!isComponentEnabled() || !privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH)) {
            return;
        }

        for (PushListener listener : internalPushListeners) {
            listener.onPushReceived(message, notificationPosted);
        }

        boolean isInternal = message.isRemoteDataUpdate() || message.isPing();
        if (!isInternal) {
            for (PushListener listener : pushListeners) {
                listener.onPushReceived(message, notificationPosted);
            }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void onNotificationPosted(@NonNull PushMessage message, int notificationId, @Nullable String notificationTag) {
        if (!isComponentEnabled() || !privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH)) {
            return;
        }
        NotificationListener listener = notificationListener;
        if (listener != null) {
            NotificationInfo info = new NotificationInfo(message, notificationId, notificationTag);
            listener.onNotificationPosted(info);
        }
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
     * Gets the push token.
     *
     * @return The push token.
     */
    @Nullable
    public String getPushToken() {
        return preferenceDataStore.getString(PUSH_TOKEN_KEY, null);
    }

    /**
     * Clear the push token.
     *
     */
    private void clearPushToken() {
        preferenceDataStore.remove(PUSH_TOKEN_KEY);
        preferenceDataStore.remove(PUSH_DELIVERY_TYPE);
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
                Logger.debug(e, "Unable to parse canonical Ids.");
            }

            List<JsonValue> canonicalIds = jsonList == null ? new ArrayList<>() : jsonList.getList();

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

    /**
     * Performs push registration.
     *
     * @return {@code true} if push registration either succeeded or is not possible on this device. {@code false} if
     * registration failed and should be retried.
     */
    @NonNull
    JobResult performPushRegistration(boolean updateChannelOnChange) {
        shouldDispatchUpdateTokenJob = false;
        String currentToken = getPushToken();
        final PushProvider provider = pushProvider;

        if (provider == null) {
            Logger.info("PushManager - Push registration failed. Missing push provider.");
            return JobResult.SUCCESS;
        }

        if (!provider.isAvailable(context)) {
            Logger.warn("PushManager - Push registration failed. Push provider unavailable: %s", provider);
            return JobResult.RETRY;
        }

        String token;
        try {
            token = provider.getRegistrationToken(context);
        } catch (PushProvider.RegistrationException e) {
            if (e.isRecoverable()) {
                Logger.debug("Push registration failed with error: %s. Will retry.", e.getMessage());
                Logger.verbose(e);
                clearPushToken();
                return JobResult.RETRY;
            } else {
                Logger.error(e, "PushManager - Push registration failed.");
                clearPushToken();
                return JobResult.SUCCESS;
            }
        }

        if (token != null && !UAStringUtil.equals(token, currentToken)) {
            Logger.info("PushManager - Push registration updated.");

            preferenceDataStore.put(PUSH_DELIVERY_TYPE, provider.getDeliveryType());
            preferenceDataStore.put(PUSH_TOKEN_KEY, token);

            for (PushTokenListener listener : pushTokenListeners) {
                listener.onPushTokenUpdated(token);
            }

            if (updateChannelOnChange) {
                airshipChannel.updateRegistration();
            }
        }

        return JobResult.SUCCESS;

    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    List<InternalNotificationListener> getInternalNotificationListeners() {
        return internalNotificationListeners;
    }

    @NonNull
    private Map<String, String> createAnalyticsHeaders() {
        if (isComponentEnabled() && privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH)) {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-UA-Channel-Opted-In", Boolean.toString(isOptIn()));
            headers.put("X-UA-Channel-Background-Enabled", Boolean.toString(isPushAvailable()));
            return headers;
        } else {
            return Collections.emptyMap();
        }
    }

    void onTokenChanged(@Nullable Class<? extends PushProvider> pushProviderClass, @Nullable String token) {
        if (privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH) && pushProvider != null) {
            if (pushProviderClass != null && pushProvider.getClass().equals(pushProviderClass)) {
                String oldToken = preferenceDataStore.getString(PUSH_TOKEN_KEY, null);
                if (token != null && !UAStringUtil.equals(token, oldToken)) {
                    clearPushToken();
                }
            }
            dispatchUpdateJob();
        }
    }

}
