/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.annotation.XmlRes
import androidx.core.util.Consumer
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.AirshipExecutors
import com.urbanairship.Platform
import com.urbanairship.Predicate
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PushProviders
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor.Companion.shared
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionDelegate
import com.urbanairship.permission.PermissionPromptFallback
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager.Companion.EXTRA_NOTIFICATION_BUTTON_FOREGROUND
import com.urbanairship.push.PushManager.Companion.EXTRA_NOTIFICATION_BUTTON_ID
import com.urbanairship.push.PushManager.Companion.EXTRA_NOTIFICATION_ID
import com.urbanairship.push.PushManager.Companion.EXTRA_PUSH_MESSAGE_BUNDLE
import com.urbanairship.push.PushProvider.PushProviderUnavailableException
import com.urbanairship.push.PushProvider.RegistrationException
import com.urbanairship.push.PushProviderType.Companion.from
import com.urbanairship.push.notifications.AirshipNotificationProvider
import com.urbanairship.push.notifications.NotificationActionButtonGroup
import com.urbanairship.push.notifications.NotificationChannelRegistry
import com.urbanairship.push.notifications.NotificationProvider
import java.util.concurrent.ExecutorService
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * This class is the primary interface for customizing the display and behavior
 * of incoming push notifications.
 */
public open class PushManager @VisibleForTesting internal constructor(
    context: Context,
    internal val preferenceDataStore: PreferenceDataStore,
    private val config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val pushProvidersSupplier: () -> PushProviders,
    private val airshipChannel: AirshipChannel,
    private val analytics: Analytics,
    internal val permissionsManager: PermissionsManager,
    private val jobDispatcher: JobDispatcher,
    private val notificationManager: AirshipNotificationManager,
    private val activityMonitor: ActivityMonitor,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AirshipComponent(context, preferenceDataStore) {

    public var notificationChannelRegistry: NotificationChannelRegistry =
        NotificationChannelRegistry(context, config.configOptions)

    /**
     * Sets the notification provider used to build notifications from a push message
     *
     * If `null`, notification will not be displayed.
     *
     * @param notificationProvider The notification provider
     *
     * @see com.urbanairship.push.notifications.NotificationProvider
     * @see com.urbanairship.push.notifications.AirshipNotificationProvider
     * @see com.urbanairship.push.notifications.CustomLayoutNotificationProvider
     */
    public var notificationProvider: NotificationProvider =
        AirshipNotificationProvider(context, config.configOptions, notificationChannelRegistry)
    private val actionGroupMap = mutableMapOf<String, NotificationActionButtonGroup>()

    public var notificationListener: NotificationListener? = null
    private val pushTokenListeners = mutableListOf<PushTokenListener>()
    private val pushListeners = mutableListOf<PushListener>()
    private val internalPushListeners = mutableListOf<PushListener>()
    private val internalNotificationListeners = mutableListOf<InternalNotificationListener>()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val uniqueIdLock: Any = Any()

    /**
     * Gets the push provider.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var pushProvider: PushProvider? = null
        private set
    private var isPushManagerEnabled: Boolean? = null

    @Volatile
    private var shouldDispatchUpdateTokenJob = true

    @Volatile
    private var isAirshipReady = false

    /**
     * Sets a predicate that determines if a notification should be presented in the foreground or not.
     *
     * @param foregroundNotificationDisplayPredicate The display predicate.
     */
    @Volatile
    public var foregroundNotificationDisplayPredicate: Predicate<PushMessage>? = null

    internal val statusObserver: PushNotificationStatusObserver

    /**
     * @hide
     */
    init {
        actionGroupMap.putAll(
            ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_buttons)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            actionGroupMap.putAll(
                ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_button_overrides)
            )
        }

        this.statusObserver = PushNotificationStatusObserver(pushNotificationStatus)
    }


    /**
     * Creates a PushManager. Normally only one push manager instance should exist, and
     * can be accessed from [com.urbanairship.Airship.push].
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
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(
        context: Context,
        preferenceDataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        pushProvidersSupplier: () -> PushProviders,
        airshipChannel: AirshipChannel,
        analytics: Analytics,
        permissionsManager: PermissionsManager
    ) : this(
        context,
        preferenceDataStore,
        config,
        privacyManager,
        pushProvidersSupplier,
        airshipChannel,
        analytics,
        permissionsManager,
        JobDispatcher.shared(context),
        AirshipNotificationManager.from(context),
        shared(context)
    )

    public override fun init() {
        super.init()
        airshipChannel.addChannelRegistrationPayloadExtender(channelExtender)
        analytics.addHeaderDelegate { this.createAnalyticsHeaders() }
        privacyManager.addListener {
            updateManagerEnablement()
            updateStatusObserver()
        }

        permissionsManager.addAirshipEnabler { permission ->
            when(permission) {
                Permission.DISPLAY_NOTIFICATIONS -> {
                    privacyManager.enable(PrivacyManager.Feature.PUSH)
                    preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, true)
                    airshipChannel.updateRegistration()
                    updateStatusObserver()
                }
                else -> {}
            }
        }

        scope.launch {
            permissionsManager.permissionStatusUpdates.collect { (permission, _) ->
                when(permission) {
                    Permission.DISPLAY_NOTIFICATIONS -> {
                        airshipChannel.updateRegistration()
                        updateStatusObserver()
                    }
                    else -> {}
                }
            }
        }

        val defaultChannelId = config.configOptions.notificationChannel
            ?: NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL

        val delegate: PermissionDelegate = NotificationsPermissionDelegate(
            defaultChannelId,
            preferenceDataStore,
            notificationManager,
            notificationChannelRegistry,
            activityMonitor
        )

        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, delegate)
        updateManagerEnablement()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public override fun onAirshipReady() {
        isAirshipReady = true

        privacyManager.addListener { checkPermission() }

        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(milliseconds: Long) {
                checkPermission()
            }
        })

        checkPermission()
    }

    private fun checkPermission(onCheckComplete: Runnable? = null) = scope.launch {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            return@launch
        }

        val status =
            permissionsManager.checkPermissionStatus(Permission.DISPLAY_NOTIFICATIONS)
        when (status) {
            PermissionStatus.GRANTED -> {
                preferenceDataStore.put(REQUEST_PERMISSION_KEY, false)
                onCheckComplete?.run()
            }

            else -> {
                if (shouldRequestNotificationPermission()) {
                    preferenceDataStore.put(REQUEST_PERMISSION_KEY, false)
                    permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS)
                    onCheckComplete?.run()
                } else {
                    onCheckComplete?.run()
                }
            }
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) return false
        if (!activityMonitor.isAppForegrounded) return false
        if (!isAirshipReady) return false
        if (!userNotificationsEnabled) return false
        if (!preferenceDataStore.getBoolean(REQUEST_PERMISSION_KEY, true)) return false
        if (!config.configOptions.isPromptForPermissionOnUserNotificationsEnabled) return false

        return true
    }

    private fun updateManagerEnablement() {
        if (privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            if (isPushManagerEnabled == true) {
                return
            }

            isPushManagerEnabled = true
            if (pushProvider == null) {
                pushProvider = resolvePushProvider()
                val pushDeliveryType = preferenceDataStore.getString(PUSH_DELIVERY_TYPE, null)
                if (pushProvider?.deliveryType?.value != pushDeliveryType) {
                    clearPushToken()
                }
            }

            if (shouldDispatchUpdateTokenJob) {
                dispatchUpdateJob()
            }
        } else {
            if (isPushManagerEnabled != null && !shouldDispatchUpdateTokenJob) {
                return
            }

            isPushManagerEnabled = false
            preferenceDataStore.remove(PUSH_DELIVERY_TYPE)
            preferenceDataStore.remove(PUSH_TOKEN_KEY)
            shouldDispatchUpdateTokenJob = true
        }
    }

    private fun dispatchUpdateJob() {
        val jobInfo: JobInfo = JobInfo.newBuilder()
            .setAction(ACTION_UPDATE_PUSH_REGISTRATION)
            .setAirshipComponent(PushManager::class.java)
            .setConflictStrategy(JobInfo.ConflictStrategy.REPLACE)
            .build()

        jobDispatcher.dispatch(jobInfo)
    }

    private fun resolvePushProvider(): PushProvider? {
        // Existing provider class
        val pushProviders = pushProvidersSupplier()
        val existingProviderClass = preferenceDataStore.getString(PROVIDER_CLASS_KEY, null)

        // Try to use the same provider
        if (existingProviderClass?.isNotEmpty() == true) {
            pushProviders.getProvider(config.platform, existingProviderClass)?.let { return it }
        }


        // Find the best provider for the platform
        val provider = pushProviders.getBestProvider(config.platform)
        provider?.let { preferenceDataStore.put(PROVIDER_CLASS_KEY, it.javaClass.toString()) }

        return provider
    }

    private val channelExtender: AirshipChannel.Extender =
        object : AirshipChannel.Extender.Blocking {
            override fun extend(builder: ChannelRegistrationPayload.Builder): ChannelRegistrationPayload.Builder {
                if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
                    return builder
                }

                if (pushToken == null) {
                    performPushRegistration(false)
                }

                builder.setPushAddress(pushToken)
                val provider = pushProvider
                if (pushToken != null && provider?.platform == Platform.ANDROID) {
                    builder.setDeliveryType(provider.deliveryType)
                }

                return builder.setOptIn(isOptIn).setBackgroundEnabled(isPushAvailable)
            }
        }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onPerformJob(jobInfo: JobInfo): JobResult {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            return JobResult.SUCCESS
        }

        when (jobInfo.action) {
            ACTION_UPDATE_PUSH_REGISTRATION -> return performPushRegistration(true)

            ACTION_DISPLAY_NOTIFICATION -> {
                val message = PushMessage.fromJsonValue(jobInfo.extras.opt(PushProviderBridge.EXTRA_PUSH))
                val providerClass = jobInfo.extras.opt(PushProviderBridge.EXTRA_PROVIDER_CLASS).string
                    ?: return JobResult.SUCCESS

                runBlocking {
                    IncomingPushRunnable.Builder(context)
                        .setLongRunning(true)
                        .setProcessed(true)
                        .setMessage(message)
                        .setProviderClass(providerClass)
                        .build()
                        .run()
                }
            }
        }

        return JobResult.SUCCESS
    }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val isPushEnabled: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.Feature.PUSH)

    /**
     * Whether user-facing push notifications are enabled.
     *
     * User notifications are push notifications that contain an alert message and are
     * intended to be shown to the user.
     *
     * This setting is persisted between application starts, so there is no need to set this
     * repeatedly. It is only necessary to set this when a user preference has changed.
     */
    public var userNotificationsEnabled: Boolean
        get() {
            return preferenceDataStore.getBoolean(USER_NOTIFICATIONS_ENABLED_KEY, false)
        }
        set(enabled) {
            if (userNotificationsEnabled == enabled) {
                return
            }

            preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, enabled)
            if (enabled) {
                preferenceDataStore.put(REQUEST_PERMISSION_KEY, true)
                checkPermission { airshipChannel.updateRegistration() }
            } else {
                airshipChannel.updateRegistration()
            }

            updateStatusObserver()
        }

    /**
     * Enables user notifications on Airship and tries to prompt for the notification permission.
     *
     * @note This does NOT enable the [com.urbanairship.PrivacyManager.Feature.PUSH] feature.
     *
     * @param consumer A consumer that will be passed the success of the permission prompt.
     */
    public fun enableUserNotifications(
        consumer: Consumer<Boolean>
    ) {
        enableUserNotifications(promptFallback = PermissionPromptFallback.None, consumer = consumer)
    }

    /**
     * Enables user notifications on Airship and tries to prompt for the notification permission.
     *
     * @note This does NOT enable the [com.urbanairship.PrivacyManager.Feature.PUSH] feature.
     *
     * @param promptFallback Prompt fallback if the the notification permission is silently denied.
     */
    public fun enableUserNotifications(
        promptFallback: PermissionPromptFallback = PermissionPromptFallback.None
    ) {
        enableUserNotifications(promptFallback = promptFallback, consumer = null)
    }

    /**
     * Enables user notifications on Airship and tries to prompt for the notification permission.
     *
     * @note This does NOT enable the [com.urbanairship.PrivacyManager.Feature.PUSH] feature.
     *
     * @param promptFallback Prompt fallback if the the notification permission is silently denied.
     * @param consumer A consumer that will be passed the success of the permission prompt.
     */
    public fun enableUserNotifications(
        promptFallback: PermissionPromptFallback,
        consumer: Consumer<Boolean>?
    ) {
        scope.launch {
            preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, true)
            val status = permissionsManager.requestPermission(
                permission = Permission.DISPLAY_NOTIFICATIONS,
                fallback = promptFallback
            )
            consumer?.accept(status.permissionStatus == PermissionStatus.GRANTED)
            updateStatusObserver()
        }
    }

    /** Whether the app is capable of receiving push (`true` if a push token is present). */
    public val isPushAvailable: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.Feature.PUSH) && !pushToken.isNullOrEmpty()

    /** Whether the app is currently opted in for push. */
    public val isOptIn: Boolean
        get() = isPushAvailable && areNotificationsOptedIn()

    /**
     * Checks if notifications are enabled for the app and in the push manager.
     *
     * @return `true` if notifications are opted in, otherwise `false`.
     */
    public fun areNotificationsOptedIn(): Boolean {
        return userNotificationsEnabled && notificationManager.areNotificationsEnabled()
    }

    /** The send metadata of the last received push, or `null` if none exists. */
    public var lastReceivedMetadata: String?
        get() {
            return analytics.lastReceivedMetadata
        }
        set(sendMetadata) {
            analytics.lastReceivedMetadata = sendMetadata
        }

    /**
     * Adds a push listener.
     *
     * @param listener The push listener.
     */
    public fun addPushListener(listener: PushListener) {
        pushListeners.add(listener)
    }

    /**
     * Adds an internal push listener.
     *
     * @param listener The push listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addInternalPushListener(listener: PushListener) {
        internalPushListeners.add(listener)
    }

    /**
     * Removes a push listener.
     *
     * @param listener The listener.
     */
    public fun removePushListener(listener: PushListener) {
        pushListeners.remove(listener)
        internalPushListeners.remove(listener)
    }

    /**
     * Adds an Airship push notification status listener.
     *
     * @param listener The listener.
     */
    public fun addNotificationStatusListener(listener: PushNotificationStatusListener) {
        statusObserver.changeListeners.add(listener)
    }

    /**
     * Removes an Airship push notification status listener.
     *
     * @param listener The listener.
     */
    public fun removeNotificationStatusListener(listener: PushNotificationStatusListener) {
        statusObserver.changeListeners.remove(listener)
    }

    /**
     * Adds a push token listener.
     *
     * @param listener The listener.
     */
    public fun addPushTokenListener(listener: PushTokenListener) {
        pushTokenListeners.add(listener)
    }

    /**
     * Removes a push token listener.
     *
     * @param listener The listener.
     */
    public fun removePushTokenListener(listener: PushTokenListener) {
        pushTokenListeners.remove(listener)
    }

    /**
     * Adds an internal notification listener.
     *
     * @param listener The notification listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addInternalNotificationListener(listener: InternalNotificationListener) {
        internalNotificationListeners.add(listener)
    }

    /**
     * Removes an internal notification listener.
     *
     * @param listener The notification listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun removeInternalNotificationListener(listener: InternalNotificationListener) {
        internalNotificationListeners.remove(listener)
    }

    /** @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun onPushReceived(message: PushMessage, notificationPosted: Boolean) {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            return
        }

        for (listener: PushListener in internalPushListeners) {
            listener.onPushReceived(message, notificationPosted)
        }

        val isInternal = message.isRemoteDataUpdate || message.isPing
        if (!isInternal) {
            for (listener: PushListener in pushListeners) {
                listener.onPushReceived(message, notificationPosted)
            }
        }
    }

    /** @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun onNotificationPosted(
        message: PushMessage,
        notificationId: Int,
        notificationTag: String?
    ) {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            return
        }

        notificationListener?.onNotificationPosted(NotificationInfo(message, notificationId, notificationTag))
    }

    /**
     * Register a notification action group under the given name.
     *
     *
     * The provided notification builders will automatically add the actions to the
     * notification when a message is received with a group specified under the
     * [com.urbanairship.push.PushMessage.EXTRA_INTERACTIVE_TYPE]
     * key.
     *
     * @param id The id of the action group.
     * @param group The notification action group.
     */
    public fun addNotificationActionButtonGroup(id: String, group: NotificationActionButtonGroup) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            UALog.e(
                "Unable to add any notification button groups that starts with the reserved Airship prefix %s",
                UA_NOTIFICATION_BUTTON_GROUP_PREFIX
            )
            return
        }

        actionGroupMap[id] = group
    }

    /**
     * Adds notification action button groups from an xml file.
     * Example entry:
     * ```<UrbanAirshipActionButtonGroup id="custom_group">
     * <UrbanAirshipActionButton
     * foreground="true"
     * id="yes"
     * android:icon="@drawable/ua_ic_notification_button_accept"
     * android:label="@string/ua_notification_button_yes"/>
     * <UrbanAirshipActionButton
     * foreground="false"
     * id="no"
     * android:icon="@drawable/ua_ic_notification_button_decline"
     * android:label="@string/ua_notification_button_no"/>
     * </UrbanAirshipActionButtonGroup>```
     *
     * @param context The application context.
     * @param resId The xml resource ID.
     */
    public fun addNotificationActionButtonGroups(context: Context, @XmlRes resId: Int) {
        val groups = ActionButtonGroupsParser.fromXml(context, resId)
        groups.entries.forEach { (id, group) ->
            addNotificationActionButtonGroup(id, group)
        }
    }

    /**
     * Removes the notification button group under the given name.
     *
     * @param id The id of the button group to remove.
     */
    public fun removeNotificationActionButtonGroup(id: String) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            UALog.e(
                "Unable to remove any reserved Airship actions groups that begin with %s",
                UA_NOTIFICATION_BUTTON_GROUP_PREFIX
            )
            return
        }

        actionGroupMap.remove(id)
    }

    /**
     * Returns the notification action group that is registered under the given name.
     *
     * @param id The id of the action group.
     * @return The notification action group.
     */
    public fun getNotificationActionGroup(id: String?): NotificationActionButtonGroup? {
        return id?.let { actionGroupMap[it] }
    }

    /**
     * Gets the push token.
     */
    public val pushToken: String?
        get() = preferenceDataStore.getString(PUSH_TOKEN_KEY, null)

    /**
     * Clear the push token.
     */
    private fun clearPushToken() {
        preferenceDataStore.remove(PUSH_TOKEN_KEY)
        preferenceDataStore.remove(PUSH_DELIVERY_TYPE)
        updateStatusObserver()
    }

    /**
     * Gets the [PushProviderType] corresponding to the current push provider.
     */
    public val pushProviderType: PushProviderType
        get() = from(pushProvider)

    /**
     * Check to see if we've seen this ID before. If we have,
     * return false. If not, add the ID to our history and return true.
     *
     * @param canonicalId The canonical push ID for an incoming notification.
     * @return `false` if the ID exists in the history, otherwise `true`.
     */
    public fun isUniqueCanonicalId(canonicalId: String?): Boolean {
        if (canonicalId.isNullOrEmpty()) {
            return true
        }

        synchronized(uniqueIdLock) {
            val jsonList = try {
                val json = preferenceDataStore.getString(LAST_CANONICAL_IDS_KEY, null)
                JsonValue.parseString(json).list
            } catch (e: JsonException) {
                UALog.d(e, "Unable to parse canonical Ids.")
                null
            }

            val canonicalIds = jsonList?.list?.toMutableList() ?: mutableListOf()

            // Wrap the canonicalId
            val id = JsonValue.wrap(canonicalId)

            // Check if the list contains the canonicalId
            if (canonicalIds.contains(id)) {
                return false
            }

            // Add it
            canonicalIds.add(id)
            if (canonicalIds.size > MAX_CANONICAL_IDS) {
                val itemsToDrop = canonicalIds.size - MAX_CANONICAL_IDS
                canonicalIds.drop(itemsToDrop)
            }

            // Store the new list
            preferenceDataStore.put(
                LAST_CANONICAL_IDS_KEY, JsonValue.wrapOpt(canonicalIds).toString()
            )
            return true
        }
    }

    /**
     * Performs push registration.
     *
     * @return `true` if push registration either succeeded or is not possible on this device. `false` if
     * registration failed and should be retried.
     */
    public fun performPushRegistration(updateChannelOnChange: Boolean): JobResult {
        shouldDispatchUpdateTokenJob = false
//        val currentToken: String? = pushToken

        val provider = pushProvider ?: run {
            UALog.i("PushManager - Push registration failed. Missing push provider.")
            return JobResult.SUCCESS
        }

        if (!provider.isAvailable(context)) {
            UALog.w("PushManager - Push registration failed. Push provider unavailable: %s", provider)
            return JobResult.RETRY
        }

        val token = try {
            provider.getRegistrationToken(context)
        } catch (e: PushProviderUnavailableException) {
            UALog.d(
                "Push registration failed, provider unavailable. Error: ${e.message}. Will retry.",
                e
            )
            return JobResult.RETRY
        } catch (e: RegistrationException) {
            UALog.d("Push registration failed. Error: %S, Recoverable %s.", e.isRecoverable, e.message, e)
            clearPushToken()

            return if (e.isRecoverable) {
                JobResult.RETRY
            } else {
                JobResult.SUCCESS
            }
        }

        if (token != null && token != pushToken) {
            UALog.i("PushManager - Push registration updated.")

            preferenceDataStore.put(PUSH_DELIVERY_TYPE, provider.deliveryType.value)
            preferenceDataStore.put(PUSH_TOKEN_KEY, token)
            updateStatusObserver()

            for (listener: PushTokenListener in pushTokenListeners) {
                listener.onPushTokenUpdated(token)
            }

            if (updateChannelOnChange) {
                airshipChannel.updateRegistration()
            }
        }

        return JobResult.SUCCESS
    }

    /** @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getInternalNotificationListeners(): List<InternalNotificationListener> {
        return internalNotificationListeners
    }

    private fun createAnalyticsHeaders(): Map<String, String> {
        return if (privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            mapOf(
                "X-UA-Channel-Opted-In" to isOptIn.toString(),
                "X-UA-Channel-Background-Enabled" to isPushAvailable.toString()
            )
        } else {
            emptyMap()
        }
    }

    public fun onTokenChanged(pushProviderClass: Class<out PushProvider?>?, token: String?) {
        val provider = pushProvider ?: return
        if (!privacyManager.isEnabled(PrivacyManager.Feature.PUSH)) {
            return
        }

        if (pushProviderClass != null && provider.javaClass == pushProviderClass) {
            val oldToken = preferenceDataStore.getString(PUSH_TOKEN_KEY, null)
            if (token != null && token != oldToken) {
                clearPushToken()
            }
        }
        dispatchUpdateJob()
    }

    /**
     * Returns the current Airship push notification status.
     * @return A status object.
     */
    public val pushNotificationStatus: PushNotificationStatus
        get() {
            return PushNotificationStatus(
                userNotificationsEnabled,
                notificationManager.areNotificationsEnabled(),
                privacyManager.isEnabled(PrivacyManager.Feature.PUSH),
                !pushToken.isNullOrEmpty()
            )
        }

    public fun updateStatusObserver() {
        statusObserver.update(pushNotificationStatus)
    }

    public companion object {

        /**
         * Action sent as a broadcast when a notification is opened.
         *
         * Extras:
         * [EXTRA_NOTIFICATION_ID],
         * [EXTRA_PUSH_MESSAGE_BUNDLE],
         * [EXTRA_NOTIFICATION_BUTTON_ID],
         * [EXTRA_NOTIFICATION_BUTTON_FOREGROUND]
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val ACTION_NOTIFICATION_RESPONSE: String =
            "com.urbanairship.push.ACTION_NOTIFICATION_RESPONSE"

        /**
         * Action sent as a broadcast when a notification is dismissed.
         *
         *
         * Extras:
         * [EXTRA_NOTIFICATION_ID],
         * [EXTRA_PUSH_MESSAGE_BUNDLE]
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val ACTION_NOTIFICATION_DISMISSED: String =
            "com.urbanairship.push.ACTION_NOTIFICATION_DISMISSED"

        /**
         * The notification ID extra contains the ID of the notification placed in the
         * `NotificationManager` by the library.
         *
         *
         * If a `Notification` was not created, the extra will not be included.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_ID: String = "com.urbanairship.push.NOTIFICATION_ID"

        /**
         * The notification tag extra contains the tag of the notification placed in the
         * `NotificationManager` by the library.
         *
         *
         * If a `Notification` was not created, the extra will not be included.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_TAG: String = "com.urbanairship.push.NOTIFICATION_TAG"

        /**
         * The push message extra bundle.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_PUSH_MESSAGE_BUNDLE: String =
            "com.urbanairship.push.EXTRA_PUSH_MESSAGE_BUNDLE"

        /**
         * The interactive notification action button identifier extra.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_BUTTON_ID: String =
            "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ID"

        /**
         * The flag indicating if the interactive notification action button is background or foreground.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_BUTTON_FOREGROUND: String =
            "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_FOREGROUND"

        /**
         * The CONTENT_INTENT extra is an optional intent that the notification builder can
         * supply on the notification. If set, the intent will be pulled from the notification,
         * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_CONTENT_INTENT: String =
            "com.urbanairship.push.EXTRA_NOTIFICATION_CONTENT_INTENT"

        /**
         * The DELETE_INTENT extra is an optional intent that the notification builder can
         * supply on the notification. If set, the intent will be pulled from the notification,
         * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_DELETE_INTENT: String =
            "com.urbanairship.push.EXTRA_NOTIFICATION_DELETE_INTENT"

        /**
         * The description of the notification action button.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION: String =
            "com.urbanairship.push.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION"

        /**
         * The actions payload for the notification action button.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD: String =
            "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD"

        /**
         * Key to store the push canonical IDs for push deduping.
         */
        private const val LAST_CANONICAL_IDS_KEY: String =
            "com.urbanairship.push.LAST_CANONICAL_IDS"

        /**
         * Max amount of canonical IDs to store.
         */
        private const val MAX_CANONICAL_IDS: Int = 10

        /**
         * Action to display a notification.
         */
        public const val ACTION_DISPLAY_NOTIFICATION: String = "ACTION_DISPLAY_NOTIFICATION"

        /**
         * Action to update push registration.
         */
        public const val ACTION_UPDATE_PUSH_REGISTRATION: String = "ACTION_UPDATE_PUSH_REGISTRATION"

        @JvmField
        public val PUSH_EXECUTOR: ExecutorService = AirshipExecutors.threadPoolExecutor()

        private const val KEY_PREFIX: String = "com.urbanairship.push"

        public const val USER_NOTIFICATIONS_ENABLED_KEY: String =
            "$KEY_PREFIX.USER_NOTIFICATIONS_ENABLED"
        public const val PUSH_DELIVERY_TYPE: String = "$KEY_PREFIX.PUSH_DELIVERY_TYPE"
        public const val PROVIDER_CLASS_KEY: String =
            "com.urbanairship.application.device.PUSH_PROVIDER"
        public const val PUSH_TOKEN_KEY: String = "$KEY_PREFIX.REGISTRATION_TOKEN_KEY"
        public const val REQUEST_PERMISSION_KEY: String = "$KEY_PREFIX.REQUEST_PERMISSION_KEY"
        private const val UA_NOTIFICATION_BUTTON_GROUP_PREFIX: String = "ua_"
    }
}
