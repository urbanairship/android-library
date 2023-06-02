/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UALog.logLevel
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.base.Extender
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobInfo.ConflictStrategy
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonValue
import com.urbanairship.locale.LocaleManager
import com.urbanairship.util.Clock
import com.urbanairship.util.Network
import com.urbanairship.util.UAStringUtil
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Airship channel access.
 */
@OpenForTesting
public class AirshipChannel internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val runtimeConfig: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val localeManager: LocaleManager,
    private val channelSubscriptions: ChannelSubscriptions,
    private val channelManager: ChannelBatchUpdateManager,
    private val channelRegistrar: ChannelRegistrar,
    private val activityMonitor: ActivityMonitor = GlobalActivityMonitor.shared(context),
    private val jobDispatcher: JobDispatcher = JobDispatcher.shared(context),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    updateDispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AirshipComponent(context, dataStore) {

    private val airshipChannelListeners: MutableList<AirshipChannelListener> = CopyOnWriteArrayList()
    private val tagLock = ReentrantLock()
    private val scope = CoroutineScope(updateDispatcher + SupervisorJob())

    internal constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        localeManager: LocaleManager,
        audienceOverridesProvider: AudienceOverridesProvider
    ) : this(
        context, dataStore, runtimeConfig, privacyManager, localeManager,
        ChannelSubscriptions(
            runtimeConfig, audienceOverridesProvider
        ),
        ChannelBatchUpdateManager(
            dataStore, runtimeConfig, audienceOverridesProvider
        ),
        ChannelRegistrar(
            context, dataStore, runtimeConfig
        )
    )

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val authTokenProvider: AuthTokenProvider = ChannelAuthTokenProvider(runtimeConfig) { id }

    /**
     * Determines whether tags are enabled on the device.
     */
    public var channelTagRegistrationEnabled: Boolean = true

    private var _isChannelCreationDelayEnabled: Boolean = false

    /**
     * Determines whether channel creation is initially disabled, to be enabled later
     * by enableChannelCreation.
     *
     * @return `true` if channel creation is initially disabled, `false` otherwise.
     */
    public val isChannelCreationDelayEnabled: Boolean
        get() { return _isChannelCreationDelayEnabled }

    /**
     * Channel Id flow. Can be used to listen for when the channel is created.
     */
    public var channelIdFlow: StateFlow<String?> = channelRegistrar.channelIdFlow

    init {
        channelRegistrar.channelId?.let {
            if (logLevel < Log.ASSERT && it.isNotEmpty()) {
                Log.d(UAirship.getAppName() + " Channel ID", it)
            }
        }

        channelRegistrar.addChannelRegistrationPayloadExtender {
            extendPayload(it)
        }

        _isChannelCreationDelayEnabled =
            channelRegistrar.channelId == null && runtimeConfig.configOptions.channelCreationDelayEnabled

        privacyManager.addListener {
            if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                tagLock.withLock { dataStore.remove(TAGS_KEY) }
                channelManager.clearPending()
            }
            updateRegistration()
        }

        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(time: Long) {
                updateRegistration()
            }
        })

        localeManager.addListener { updateRegistration() }

        val startedId = channelRegistrar.channelId
        // Channel created
        scope.launch {
            channelRegistrar.channelIdFlow
                .mapNotNull { it }
                .filter { it != startedId }
                .collect { channelId ->
                    // intent
                    if (runtimeConfig.configOptions.extendedBroadcastsEnabled) {
                        // Send ChannelCreated intent for other plugins that depend on Airship
                        val channelCreatedIntent =
                            Intent(ACTION_CHANNEL_CREATED).setPackage(UAirship.getPackageName())
                                .addCategory(UAirship.getPackageName())
                                .putExtra(UAirship.EXTRA_CHANNEL_ID_KEY, channelId)
                        try {
                            context.sendBroadcast(channelCreatedIntent)
                        } catch (e: Exception) {
                            UALog.e(e) { "Failed to send channel create intent" }
                        }
                    }

                    // Listeners
                    airshipChannelListeners.forEach {
                        it.onChannelCreated(channelId)
                    }
                }
        }
    }

    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)
        updateRegistration()
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addChannelRegistrationPayloadExtender(extender: Extender<ChannelRegistrationPayload.Builder>) {
        channelRegistrar.addChannelRegistrationPayloadExtender(extender)
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun removeChannelRegistrationPayloadExtender(extender: Extender<ChannelRegistrationPayload.Builder>) {
        channelRegistrar.removeChannelRegistrationPayloadExtender(extender)
    }

    private val isRegistrationAllowed: Boolean
        get() {
            if (!isComponentEnabled) {
                return false
            }

            if (id != null) {
                return true
            }

            return !isChannelCreationDelayEnabled && privacyManager.isAnyFeatureEnabled
        }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @WorkerThread
    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        if (!isRegistrationAllowed) {
            UALog.d { "Channel registration is currently disabled." }
            return JobResult.SUCCESS
        }

        return runBlocking {
            // Perform CRA first
            val registrationResult = channelRegistrar.updateRegistration()
            if (registrationResult == RegistrationResult.FAILED) {
                return@runBlocking JobResult.FAILURE
            }

            val channelId = channelRegistrar.channelId ?: return@runBlocking JobResult.SUCCESS

            if (!channelManager.uploadPending(channelId)) {
                return@runBlocking JobResult.FAILURE
            }

            if (registrationResult == RegistrationResult.NEEDS_UPDATE || channelManager.hasPending) {
                dispatchUpdateJob(conflictStrategy = JobInfo.REPLACE)
            }

            JobResult.SUCCESS
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    override fun getComponentGroup(): Int = AirshipComponentGroups.CHANNEL

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun onComponentEnableChange(isEnabled: Boolean) {
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    override fun onUrlConfigUpdated() {
        updateRegistration()
    }

    /**
     * Gets the channel identifier. This Id is created asynchronously, so initially it may be null.
     * To be notified when the channel is updated, add a listener with [.addChannelListener].
     *
     * @return The channel Id, or null if the Id is not yet created.
     */
    public val id: String?
        get() = channelRegistrar.channelId

    /**
     * Adds a channel listener.
     *
     * @param listener The listener.
     */
    public fun addChannelListener(listener: AirshipChannelListener) {
        airshipChannelListeners.add(listener)
    }

    /**
     * Removes a channel listener.
     *
     * @param listener The listener.
     */
    public fun removeChannelListener(listener: AirshipChannelListener) {
        airshipChannelListeners.remove(listener)
    }

    /**
     * Edits channel Tags.
     *
     * @return A [TagEditor]
     */
    public fun editTags(): TagEditor {
        return object : TagEditor() {
            override fun onApply(
                clear: Boolean,
                tagsToAdd: Set<String>,
                tagsToRemove: Set<String>
            ) {
                tagLock.withLock {
                    if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                        UALog.w { "AirshipChannel - Unable to apply tag group edits when opted out of tags and attributes." }
                        return
                    }
                    val tags: MutableSet<String> =
                        if (clear) mutableSetOf() else this@AirshipChannel.tags.toMutableSet()
                    tags.addAll(tagsToAdd)
                    tags.removeAll(tagsToRemove)
                    this@AirshipChannel.tags = tags
                }
            }
        }
    }

    /**
     * Edit the channel tag groups.
     *
     * @return A [TagGroupsEditor].
     */
    public fun editTagGroups(): TagGroupsEditor {
        return object : TagGroupsEditor() {
            override fun allowTagGroupChange(tagGroup: String): Boolean {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP == tagGroup) {
                    UALog.e { "Unable to add tags to $tagGroup tag group when `channelTagRegistrationEnabled` is true." }
                    return false
                }
                return true
            }

            override fun onApply(collapsedMutations: List<TagGroupsMutation>) {
                if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                    UALog.w { "Unable to apply channel tag edits when opted out of tags and attributes." }
                    return
                }

                if (collapsedMutations.isNotEmpty()) {
                    channelManager.addUpdate(tags = collapsedMutations)
                    updateRegistration()
                }
            }
        }
    }

    /**
     * Edit the attributes associated with this channel.
     *
     * @return An [AttributeEditor].
     */
    public fun editAttributes(): AttributeEditor {
        return object : AttributeEditor(clock) {
            override fun onApply(mutations: List<AttributeMutation>) {
                if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                    UALog.w { "AirshipChannel - Unable to apply attribute edits when opted out of tags and attributes." }
                    return
                }

                if (mutations.isNotEmpty()) {
                    channelManager.addUpdate(attributes = mutations)
                    updateRegistration()
                }
            }
        }
    }

    /**
     * Set tags for the channel and update the server.
     *
     *
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     *
     *
     * To clear the current set of tags, pass an empty set to this method.
     */
    public var tags: Set<String>
        get() {
            tagLock.withLock {
                if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                    return emptySet()
                }

                val tags = dataStore.getJsonValue(TAGS_KEY).optList().mapNotNull {
                    it.string
                }.toSet()

                val normalizedTags = TagUtils.normalizeTags(tags)
                // To prevent the getTags call from constantly logging tag set failures, sync tags
                if (tags.size != normalizedTags.size) {
                    this.tags = normalizedTags
                }
                return normalizedTags
            }
        }
        set(tags) {
            tagLock.withLock {
                if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                    UALog.w { "AirshipChannel - Unable to apply attribute edits when opted out of tags and attributes." }
                    return
                }
                val normalizedTags = TagUtils.normalizeTags(tags)
                dataStore.put(TAGS_KEY, JsonValue.wrapOpt(normalizedTags))
            }
            updateRegistration()
        }

    /**
     * Returns the current set of subscription lists for the channel.
     *
     * An empty set indicates that this channel is not subscribed to any lists.
     *
     * @return A [PendingResult] of the current set of subscription lists.
     */
    public fun fetchSubscriptionListsPendingResult(): PendingResult<Set<String>> {
        val pendingResult = PendingResult<Set<String>>()
        scope.launch {
            pendingResult.result = fetchSubscriptionLists().getOrNull()
        }
        return pendingResult
    }

    /**
     * Returns the current set of subscription lists for the channel.
     *
     * An empty set indicates that this channel is not subscribed to any lists.
     *
     * @return A [Result] of the current set of subscription lists.
     */
    @JvmSynthetic
    public suspend fun fetchSubscriptionLists(): Result<Set<String>> {
        if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
            return Result.failure(
                IllegalStateException("Unable to fetch subscriptions when FEATURE_TAGS_AND_ATTRIBUTES are disabled")
            )
        }

        if (!isRegistrationAllowed) {
            return Result.failure(
                IllegalStateException("Unable to fetch subscriptions when channel registration is disabled")
            )
        }

        val channelId = channelIdFlow.mapNotNull { it }.first()
        return channelSubscriptions.fetchSubscriptionLists(channelId)
    }

    /**
     * Edit the channel subscription lists.
     *
     * @return a [SubscriptionListEditor].
     */
    public fun editSubscriptionLists(): SubscriptionListEditor {
        return object : SubscriptionListEditor(clock) {
            override fun onApply(collapsedMutations: List<SubscriptionListMutation>) {
                if (!privacyManager.isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                    UALog.w { "AirshipChannel - Unable to apply subscription list edits when opted out of tags and attributes." }
                    return
                }

                if (collapsedMutations.isNotEmpty()) {
                    channelManager.addUpdate(subscriptions = collapsedMutations)
                    updateRegistration()
                }
            }
        }
    }

    /**
     * Enables channel creation if channel creation has been delayed.
     *
     *
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when channelCreationDelayEnabled has been
     * set to `true` in the airship config.
     */
    public fun enableChannelCreation() {
        if (isChannelCreationDelayEnabled) {
            _isChannelCreationDelayEnabled = false
            updateRegistration()
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun trackLiveUpdateMutation(mutation: LiveUpdateMutation) {
        channelManager.addUpdate(liveUpdates = listOf(mutation))
        updateRegistration()
    }

    /**
     * Updates registration.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun updateRegistration() {
        dispatchUpdateJob(JobInfo.KEEP)
    }

    private fun dispatchUpdateJob(
        @ConflictStrategy conflictStrategy: Int
    ) {
        if (!isRegistrationAllowed || !runtimeConfig.urlConfig.isDeviceUrlAvailable) {
            return
        }

        val jobInfo = JobInfo.newBuilder()
            .setAction(ACTION_UPDATE_CHANNEL)
            .setNetworkAccessRequired(true)
            .setAirshipComponent(AirshipChannel::class.java)
            .setConflictStrategy(conflictStrategy)
            .build()

        jobDispatcher.dispatch(jobInfo)
    }

    private fun extendPayload(builder: ChannelRegistrationPayload.Builder): ChannelRegistrationPayload.Builder {
        val shouldSetTags = channelTagRegistrationEnabled

        builder.setTags(shouldSetTags, if (shouldSetTags) tags else null)
            .setIsActive(activityMonitor.isAppForegrounded)

        when (runtimeConfig.platform) {
            UAirship.ANDROID_PLATFORM -> builder.setDeviceType(ChannelRegistrationPayload.ANDROID_DEVICE_TYPE)
            UAirship.AMAZON_PLATFORM -> builder.setDeviceType(ChannelRegistrationPayload.AMAZON_DEVICE_TYPE)
            else -> throw IllegalStateException("Unable to get platform")
        }
        if (privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS)) {
            UAirship.getPackageInfo()?.versionName?.let {
                builder.setAppVersion(it)
            }
            builder.setCarrier(Network.getCarrier())
            builder.setDeviceModel(Build.MODEL)
            builder.setApiVersion(Build.VERSION.SDK_INT)
        }
        if (privacyManager.isAnyFeatureEnabled) {
            builder.setTimezone(TimeZone.getDefault().id)
            val locale = localeManager.locale
            if (!UAStringUtil.isEmpty(locale.country)) {
                builder.setCountry(locale.country)
            }
            if (!UAStringUtil.isEmpty(locale.language)) {
                builder.setLanguage(locale.language)
            }
            builder.setSdkVersion(UAirship.getVersion())
        }

        return builder
    }

    internal companion object {

        /**
         * Broadcast that is sent when a channel has been created.
         */
        private const val DEFAULT_TAG_GROUP = "device"

        /**
         * Broadcast that is sent when a channel has been created.
         */
        internal const val ACTION_CHANNEL_CREATED = "com.urbanairship.CHANNEL_CREATED"

        // PreferenceDataStore keys
        private const val TAGS_KEY = "com.urbanairship.push.TAGS"

        private const val ACTION_UPDATE_CHANNEL = "ACTION_UPDATE_CHANNEL"
    }
}
