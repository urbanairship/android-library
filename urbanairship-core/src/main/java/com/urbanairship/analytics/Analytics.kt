/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipExecutors
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.analytics.data.EventManager
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.util.Clock
import com.urbanairship.util.UAStringUtil
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


/**
 * This class is the primary interface to the Airship Analytics API.
 */
public class Analytics
@VisibleForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val runtimeConfig: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val airshipChannel: AirshipChannel,
    private val activityMonitor: ActivityMonitor,
    private val localeManager: LocaleManager,
    private val executor: Executor,
    private val eventManager: EventManager,
    private val permissionsManager: PermissionsManager,
    private val eventFeed: AirshipEventFeed,
    private val clock: Clock = Clock.DEFAULT_CLOCK
    ) : AirshipComponent(context, dataStore) {

    /**
     * Delegate to add analytics headers.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun interface AnalyticsHeaderDelegate {
        public fun onCreateAnalyticsHeaders(): Map<String, String>
    }

    private val _events: MutableSharedFlow<AirshipEventData> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * A shared flow of raw event tracked by Airship.
     */
    public val events: SharedFlow<AirshipEventData> = _events.asSharedFlow()

    private val listener: ApplicationListener =  object : ApplicationListener {
        override fun onForeground(time: Long) {
            this@Analytics.onForeground(time)
        }

        override fun onBackground(time: Long) {
            this@Analytics.onBackground(time)
        }
    }

    private val headerDelegates: MutableList<AnalyticsHeaderDelegate> = CopyOnWriteArrayList()
    private val associatedIdentifiersLock = Any()

    /**
     * Gets the current environment Id.
     *
     * @return A environment Id String.
     */
    // Session state
    public var sessionId: String = UUID.randomUUID().toString()
        private set

    /**
     * Returns the last stored send Id from when a push conversion was detected.
     *
     * @return A send Id String.
     */
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var conversionSendId: String? = null
        /**
         * Stores the send id for later retrieval when a push conversion has been detected.
         * You should not call this method directly.
         *
         * @param sendId The associated send Id String.
         * @hide
         */
        set(sendId) {
            UALog.d { "Setting conversion send ID: $sendId" }
            field = sendId
        }

    /**
     * Returns the last stored send metadata from when a push conversion was detected.
     *
     * @return A metadata String.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var conversionMetadata: String? = null
        /**
         * Stores the send metadata for later retrieval when a push conversion has been detected.
         * You should not call this method directly.
         *
         * @param metadata The associated metadata String.
         * @hide
         */
        set(metadata) {
            UALog.d { "Setting conversion metadata: $metadata" }
            field = metadata
        }

    /**
     * Returns the send metadata of the last received push.
     *
     * @return The send metadata from the last received push, or null if not found.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var lastReceivedMetadata: String?
        get() {
            return dataStore.getString(LAST_RECEIVED_METADATA, null)
        }
        set(value) {
            dataStore.put(LAST_RECEIVED_METADATA, value)
        }

    // Screen state
    private var _currentScreen: MutableStateFlow<String?> = MutableStateFlow(null)
    private var previousScreen: String? = null
    private var screenStartTime: Long = 0

    /**
     * The name of the screen that is currently being tracked by [trackScreen]
     */
    public val screenState: StateFlow<String?> = _currentScreen.asStateFlow()

    private var _regions: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /**
     * The current list of region IDs tracked using [com.urbanairship.analytics.location.RegionEvent].
     */
    public val regionState: StateFlow<Set<String>> = _regions.asStateFlow()

    private val sdkExtensions: MutableList<String> = ArrayList()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        channel: AirshipChannel,
        localeManager: LocaleManager,
        permissionsManager: PermissionsManager,
        eventFeed: AirshipEventFeed
    ) : this(
        context,
        dataStore,
        runtimeConfig,
        privacyManager,
        channel,
        GlobalActivityMonitor.shared(context),
        localeManager,
        AirshipExecutors.newSerialExecutor(),
        EventManager(context, dataStore, runtimeConfig),
        permissionsManager,
        eventFeed
    )

    /**
     * Adds an analytic header delegate.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addHeaderDelegate(headerDelegate: AnalyticsHeaderDelegate) {
        headerDelegates.add(headerDelegate)
    }

    init {
        activityMonitor.addApplicationListener(listener)
        if (activityMonitor.isAppForegrounded) {
            onForeground(clock.currentTimeMillis())
        }

        airshipChannel.addChannelListener(
            object : AirshipChannelListener {
                override fun onChannelCreated(channelId: String) = uploadEvents()
            }
        )

        privacyManager.addListener {
            if (!privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
                clearPendingEvents()
                synchronized(associatedIdentifiersLock) {
                    dataStore.remove(
                        ASSOCIATED_IDENTIFIERS_KEY
                    )
                }
            }
        }
    }

    override fun tearDown() {
        activityMonitor.removeApplicationListener(listener)
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onPerformJob(airship: Airship, jobInfo: JobInfo): JobResult {
        return when(jobInfo.action) {
            EventManager.ACTION_SEND -> {
                if (!isEnabled) {
                    return JobResult.SUCCESS
                }

                val channelId = airshipChannel.id
                if (channelId == null) {
                    UALog.d { "No channel ID, skipping analytics send." }
                    return JobResult.SUCCESS
                }

                if (eventManager.uploadEvents(channelId, analyticHeaders)) {
                    JobResult.SUCCESS
                } else {
                    JobResult.RETRY
                }
            }
            else -> JobResult.SUCCESS
        }
    }

    /**
     * Records a region event.
     * @param event The region event.
     */
    public fun recordCustomEvent(event: CustomEvent) {
        if (!addEvent(event)) {
            return
        }

        eventFeed.emit(
            AirshipEventFeed.Event.Analytics(
                event.type,
                event.toJsonValue(),
                event.eventValue?.toDouble()
            )
        )
    }

    /**
     * Records a region event.
     * @param event The region event.
     */
    public fun recordRegionEvent(event: RegionEvent) {
        if (!addEvent(event)) {
            return
        }

        when (event.boundaryEvent) {
            RegionEvent.Boundary.ENTER -> {
                _regions.update {
                    it.toMutableSet().apply { add(event.regionId) }.toSet()
                }
            }

            RegionEvent.Boundary.EXIT -> {
                _regions.update {
                    it.toMutableSet().apply { remove(event.regionId) }.toSet()
                }
            }
        }
    }

    /**
     * @Hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addEvent(event: Event): Boolean {
        if (!event.isValid()) {
            UALog.e { "Analytics - Invalid event: $event" }
            return false
        }
        if (!isEnabled) {
            UALog.d { "Disabled ignoring event: ${event.type}" }
            return false
        }

        val conversionData = ConversionData(
            conversionSendId = conversionSendId,
            conversionMetadata = conversionMetadata,
            lastReceivedMetadata = lastReceivedMetadata
        )

        val eventData = AirshipEventData(
            id = event.eventId,
            sessionId = sessionId,
            body = event.getEventData(conversionData).toJsonValue(),
            type = event.type,
            timeMs = event.timeMilliseconds
        )

        val feedEvent = when(event) {
            is CustomEvent -> {
                AirshipEventFeed.Event.Analytics(
                    event.type,
                    event.toJsonValue(),
                    event.eventValue?.toDouble()
                )
            }
            else -> {
                AirshipEventFeed.Event.Analytics(
                    event.type,
                    eventData.body
                )
            }
        }

        eventFeed.emit(feedEvent)

        UALog.v { "Adding event: ${event.type}" }
        executor.execute { eventManager.addEvent(eventData, event.priority) }
        _events.tryEmit(eventData)
        return true
    }

    /**
     * Called when the app is foregrounded.
     *
     * @param timeMS Time of foregrounding.
     */
    private fun onForeground(timeMS: Long) {
        // Start a new environment when the app enters the foreground
        sessionId = UUID.randomUUID().toString()
        UALog.d { "New session: $sessionId" }

        // If the app backgrounded, there should be no current screen
        if (screenState.value == null && previousScreen != null) {
            trackScreen(previousScreen)
        }
        addEvent(AppForegroundEvent(timeMS))
    }

    /**
     * Called when the app is backgrounded.
     *
     * @param timeMS Time when backgrounded.
     */
    private fun onBackground(timeMS: Long) {
        // Stop tracking screen
        trackScreen(null)
        addEvent(AppBackgroundEvent(timeMS))
        conversionSendId = null
        conversionMetadata = null
        if (privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
            eventManager.scheduleEventUpload(0.seconds)
        }
    }

    private fun clearPendingEvents() {
        executor.execute {
            UALog.i { "Deleting all analytic events." }
            eventManager.deleteEvents()
        }
    }

    public val isEnabled: Boolean
        /**
         * Returns `true` if [com.urbanairship.AirshipConfigOptions.analyticsEnabled]
         * is set to `true`, and [PrivacyManager.Feature.ANALYTICS] is enabled, otherwise `false`.
         *
         *
         * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
         * region triggers, location segmentation, push to local time).
         *
         * @return `true` if analytics is enabled, otherwise `false`.
         */
        get() {
            return runtimeConfig.configOptions.analyticsEnabled &&
                    privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)
        }

    /**
     * Edits the currently stored associated identifiers. All changes made in the editor are batched,
     * and not stored until you call apply(). Calling apply() on the editor will associate the
     * identifiers with the device and add an event that will be sent up with other analytics
     * events. See [com.urbanairship.analytics.AssociatedIdentifiers.Editor]
     *
     * @return The AssociatedIdentifiers.Editor
     */
    public fun editAssociatedIdentifiers(): AssociatedIdentifiers.Editor {
        return object : AssociatedIdentifiers.Editor() {
            public override fun onApply(
                clear: Boolean,
                idsToAdd: Map<String, String>,
                idsToRemove: List<String>
            ) {
                synchronized(associatedIdentifiersLock) {
                    if (!isEnabled) {
                        UALog.w { "Analytics - Unable to track associated identifiers when analytics is disabled." }
                        return
                    }
                    val ids: MutableMap<String, String> = HashMap()
                    val associatedIdentifiers: AssociatedIdentifiers = associatedIdentifiers
                    if (!clear) {
                        val currentIds: Map<String, String> = associatedIdentifiers.ids
                        ids.putAll(currentIds)
                    }
                    ids.putAll(idsToAdd)
                    for (key: String in idsToRemove) {
                        ids.remove(key)
                    }
                    val identifiers = AssociatedIdentifiers(ids)
                    if ((associatedIdentifiers.ids == identifiers.ids)) {
                        UALog.i { "Skipping analytics event addition for duplicate associated identifiers." }
                        return
                    }
                    dataStore.put(ASSOCIATED_IDENTIFIERS_KEY, identifiers)
                    addEvent(AssociateIdentifiersEvent(identifiers))
                }
            }
        }
    }

    /**
     * Edit the currently stored associated identifiers.
     * Automatically calls [AssociatedIdentifiers.Editor.apply]
     */
    public fun editAssociatedIdentifiers(block: AssociatedIdentifiers.Editor.() -> Unit) {
        val editor = editAssociatedIdentifiers()
        block.invoke(editor)
        editor.apply()
    }

    public val associatedIdentifiers: AssociatedIdentifiers
        /**
         * Returns the device's current associated identifiers.
         *
         * @return The current associated identifiers.
         */
        get() {
            synchronized(associatedIdentifiersLock) {
                try {
                    val value: JsonValue = dataStore.getJsonValue(ASSOCIATED_IDENTIFIERS_KEY)
                    if (!value.isNull) {
                        return AssociatedIdentifiers.fromJson(value)
                    }
                } catch (e: JsonException) {
                    UALog.e(e) { "Unable to parse associated identifiers." }
                    dataStore.remove(ASSOCIATED_IDENTIFIERS_KEY)
                }
                return AssociatedIdentifiers()
            }
        }

    /**
     * Initiates screen tracking for a specific app screen, must be called once per tracked screen.
     *
     * @param screen The screen's string identifier.
     */
    @MainThread
    public fun trackScreen(screen: String?) {
        val currentScreenValue = screenState.value
        // Prevent duplicate calls to track same screen
        if (currentScreenValue == screen) {
            return
        }

        // If there's a screen currently being tracked set its stop time and add it to analytics
        if (currentScreenValue != null) {
            val ste = ScreenTrackingEvent(
                currentScreenValue,
                previousScreen,
                screenStartTime,
                clock.currentTimeMillis()
            )

            // Set previous screen to last tracked screen
            previousScreen = currentScreenValue

            // Add screen tracking event to next analytics batch
            addEvent(ste)
        }

        _currentScreen.value = screen
        screenStartTime = clock.currentTimeMillis()
        if (screen != null) {
            eventFeed.emit(AirshipEventFeed.Event.Screen(screen))
        }
    }

    /**
     * Uploads any pending events. Events are batched and uploaded automatically to conserve
     * battery life. Normally apps should not call this method directly.
     */
    public fun uploadEvents() {
        if (privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
            eventManager.scheduleEventUpload(SCHEDULE_SEND_DELAY_SECONDS)
        }
    }

    @get:WorkerThread
    private val analyticHeaders: Map<String, String>
        get() {
            val headers: MutableMap<String, String> = HashMap()

            // Delegates
            for (delegate: AnalyticsHeaderDelegate in headerDelegates) {
                headers.putAll(delegate.onCreateAnalyticsHeaders())
            }
            for (permission: Permission in permissionsManager.configuredPermissions) {
                try {
                    val currentStatus = permissionsManager.checkPermissionStatus(permission).get()
                    if (currentStatus != null) {
                        headers["X-UA-Permission-" + permission.value] = currentStatus.value
                    }
                } catch (e: Exception) {
                    UALog.e(e) { "Failed to get status for permission $permission" }
                }
            }

            // App info
            headers["X-UA-Package-Name"] = packageName ?: ""
            headers["X-UA-Package-Version"] = packageVersion ?: ""
            headers["X-UA-Android-Version-Code"] = Build.VERSION.SDK_INT.toString()

            // Airship info
            headers["X-UA-Device-Family"] = runtimeConfig.platform.stringValue
            headers["X-UA-Lib-Version"] = Airship.getVersion()
            headers["X-UA-App-Key"] = runtimeConfig.configOptions.appKey
            headers["X-UA-In-Production"] = runtimeConfig.configOptions.inProduction.toString()
            headers["X-UA-Channel-ID"] = airshipChannel.id ?: ""
            headers["X-UA-Push-Address"] = airshipChannel.id ?: ""
            if (sdkExtensions.isNotEmpty()) {
                headers["X-UA-Frameworks"] = UAStringUtil.join(sdkExtensions, ",")
            }

            // Device info
            headers["X-UA-Device-Model"] = Build.MODEL
            headers["X-UA-Timezone"] = TimeZone.getDefault().id
            val locale = localeManager.locale
            if (!UAStringUtil.isEmpty(locale.language)) {
                headers["X-UA-Locale-Language"] = locale.language
                if (!UAStringUtil.isEmpty(locale.country)) {
                    headers["X-UA-Locale-Country"] = locale.country
                }
                if (!UAStringUtil.isEmpty(locale.variant)) {
                    headers["X-UA-Locale-Variant"] = locale.variant
                }
            }
            return headers
        }
    private val packageName: String?
        get() {
            return try {
                context.packageManager.getPackageInfo(context.packageName, 0).packageName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

    private val packageVersion: String?
        get() {
            return try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

    /**
     * Registers an SDK extension with the analytics module.
     *
     * @param extension The name of the SDK extension. The compiler will warn if the name is invalid.
     * @param version The version of the SDK extension. Commas will be removed from the string.
     * @hide
     */
    public fun registerSDKExtension(extension: Extension, version: String) {
        val normalizedVersion = version.replace(",", "")
        sdkExtensions.add("${extension.extensionName}:$normalizedVersion")
    }

    public companion object {

        /**
         * Minimum amount of delay when [.uploadEvents] is called.
         */
        private val SCHEDULE_SEND_DELAY_SECONDS = 10.seconds
        private const val ASSOCIATED_IDENTIFIERS_KEY = "com.urbanairship.analytics.ASSOCIATED_IDENTIFIERS"
        private const val LAST_RECEIVED_METADATA = "com.urbanairship.push.LAST_RECEIVED_METADATA"
    }
}
