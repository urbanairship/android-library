/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UALog.logLevel
import com.urbanairship.Airship
import com.urbanairship.JobAwareAirshipComponent
import com.urbanairship.Platform
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.channel.AirshipChannel.Extender.Blocking
import com.urbanairship.channel.AirshipChannel.Extender.Suspending
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobInfo.ConflictStrategy
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonValue
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.util.Clock
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Airship channel access.
 */
@OpenForTesting
public class AirshipChannel internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val runtimeConfig: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val permissionsManager: PermissionsManager,
    private val localeManager: LocaleManager,
    private val channelManager: ChannelBatchUpdateManager,
    private val channelRegistrar: ChannelRegistrar,
    private val subscriptionsProvider: SubscriptionsProvider,
    private val activityMonitor: ActivityMonitor = GlobalActivityMonitor.shared(context),
    private val jobDispatcher: JobDispatcher = JobDispatcher.shared(context),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    updateDispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : JobAwareAirshipComponent(context, dataStore, jobDispatcher) {

    private val airshipChannelListeners: MutableList<AirshipChannelListener> = CopyOnWriteArrayList()
    private val tagLock = ReentrantLock()
    private val scope = CoroutineScope(updateDispatcher + SupervisorJob())
    private val channelRegistrationPayloadExtenders: MutableList<Extender> = CopyOnWriteArrayList()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        permissionsManager: PermissionsManager,
        localeManager: LocaleManager,
        audienceOverridesProvider: AudienceOverridesProvider,
        channelRegistrar: ChannelRegistrar
    ) : this(
        context = context,
        dataStore = dataStore,
        runtimeConfig = runtimeConfig,
        privacyManager = privacyManager,
        permissionsManager = permissionsManager,
        localeManager = localeManager,
        channelManager = ChannelBatchUpdateManager(
            dataStore, runtimeConfig, audienceOverridesProvider
        ),
        channelRegistrar = channelRegistrar,
        subscriptionsProvider = SubscriptionsProvider(runtimeConfig,
            privacyManager,
            channelRegistrar.channelIdFlow.mapNotNull { it },
            combine(channelRegistrar.channelIdFlow.mapNotNull { it }, audienceOverridesProvider.updates) { channelId, _ ->
                audienceOverridesProvider.channelOverrides(channelId)
            })
    )


    init {
        this.runtimeConfig.addConfigListener {
            updateRegistration()
        }
    }

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

    public val subscriptions: Flow<Result<Set<String>>> = subscriptionsProvider.updates.map { it.data }


    init {
        channelRegistrar.channelId?.let {
            if (logLevel < Log.ASSERT && it.isNotEmpty()) {
                val appName = context.packageManager
                    .getApplicationLabel(context.applicationInfo)

                Log.d("$appName Channel ID", it)
            }
        }

        channelRegistrar.payloadBuilder = {
            this.buildCraPayload()
        }

        _isChannelCreationDelayEnabled =
            channelRegistrar.channelId == null && runtimeConfig.configOptions.channelCreationDelayEnabled

        privacyManager.addListener {
            if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
                tagLock.withLock { dataStore.remove(TAGS_KEY) }
                channelManager.clearPending()
            }
            updateRegistration()
        }

        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(milliseconds: Long) {
                updateRegistration()
            }
        })

        scope.launch {
            localeManager.localeUpdates.collect { updateRegistration() }
        }

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
                        val channelCreatedIntent = Intent(ACTION_CHANNEL_CREATED)
                            .setPackage(context.packageName)
                            .addCategory(context.packageName)
                            .putExtra(Airship.EXTRA_CHANNEL_ID_KEY, channelId)
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

    override fun onAirshipReady() {
        updateRegistration()
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addChannelRegistrationPayloadExtender(extender: Extender) {
        channelRegistrationPayloadExtenders.add(extender)
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun removeChannelRegistrationPayloadExtender(extender: Extender) {
        channelRegistrationPayloadExtenders.remove(extender)
    }

    private val isRegistrationAllowed: Boolean
        get() {
            if (id != null) {
                return true
            }

            return !isChannelCreationDelayEnabled && privacyManager.isAnyFeatureEnabled
        }

    override val jobActions: List<String>
        get() = listOf(ACTION_UPDATE_CHANNEL)

    override suspend fun onPerformJob(jobInfo: JobInfo): JobResult {
        if (!isRegistrationAllowed) {
            UALog.d { "Channel registration is currently disabled." }
            return JobResult.SUCCESS
        }

        // Perform CRA first
        val registrationResult = channelRegistrar.updateRegistration()
        if (registrationResult == RegistrationResult.FAILED) {
            return JobResult.FAILURE
        }

        val channelId = channelRegistrar.channelId ?: return JobResult.SUCCESS

        if (!channelManager.uploadPending(channelId)) {
            return JobResult.FAILURE
        }

        if (registrationResult == RegistrationResult.NEEDS_UPDATE || channelManager.hasPending) {
            dispatchUpdateJob(conflictStrategy = ConflictStrategy.REPLACE)
        }

        return JobResult.SUCCESS
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
                    if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
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
     * Edits channel Tags. Automatically calls [TagEditor.apply].
     */
    @JvmSynthetic
    public fun editTags(block: TagEditor.() -> Unit) {
        val editor = editTags()
        block.invoke(editor)
        editor.apply()
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
                if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
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
     * Edits channel tag groups. Automatically calls [TagGroupsEditor.apply].
     */
    @JvmSynthetic
    public fun editTagGroups(block: TagGroupsEditor.() -> Unit) {
        val editor = editTagGroups()
        block.invoke(editor)
        editor.apply()
    }

    /**
     * Edit the attributes associated with this channel.
     *
     * @return An [AttributeEditor].
     */
    public fun editAttributes(): AttributeEditor {
        return object : AttributeEditor(clock) {
            override fun onApply(collapsedMutations: List<AttributeMutation>) {
                if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
                    UALog.w { "AirshipChannel - Unable to apply attribute edits when opted out of tags and attributes." }
                    return
                }

                if (collapsedMutations.isNotEmpty()) {
                    channelManager.addUpdate(attributes = collapsedMutations)
                    updateRegistration()
                }
            }
        }
    }

    /**
     * Edits the attributes associated with this channel. Automatically calls [AttributeEditor.apply].
     */
    @JvmSynthetic
    public fun editAttributes(block: AttributeEditor.() -> Unit) {
        val editor = editAttributes()
        block.invoke(editor)
        editor.apply()
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
                if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
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
                if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
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
            pendingResult.setResult(fetchSubscriptionLists().getOrNull())
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
        return subscriptionsProvider.updates.first().data
    }

    /**
     * Edit the channel subscription lists.
     *
     * @return a [SubscriptionListEditor].
     */
    public fun editSubscriptionLists(): SubscriptionListEditor {
        return object : SubscriptionListEditor(clock) {
            override fun onApply(collapsedMutations: List<SubscriptionListMutation>) {
                if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
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
     * Edits the channel subscription lists. Automatically calls [SubscriptionListEditor.apply].
     */
    @JvmSynthetic
    public fun editSubscriptionLists(block: SubscriptionListEditor.() -> Unit) {
        val editor = editSubscriptionLists()
        block.invoke(editor)
        editor.apply()
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
        dispatchUpdateJob(ConflictStrategy.KEEP)
    }

    private fun dispatchUpdateJob(conflictStrategy: ConflictStrategy) {
        if (!isRegistrationAllowed || !runtimeConfig.isDeviceUrlAvailable) {
            return
        }

        val jobInfo = JobInfo.newBuilder()
            .setAction(ACTION_UPDATE_CHANNEL)
            .setNetworkAccessRequired(true)
            .setScope(AirshipChannel::class.java.name)
            .setConflictStrategy(conflictStrategy)
            .build()

        jobDispatcher.dispatch(jobInfo)
    }

    @Throws(PackageManager.NameNotFoundException::class, IllegalStateException::class)
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public suspend fun buildCraPayload(): ChannelRegistrationPayload {
        var builder = ChannelRegistrationPayload.Builder()

        when (runtimeConfig.platform) {
            Platform.ANDROID -> builder.setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            Platform.AMAZON -> builder.setDeviceType(ChannelRegistrationPayload.DeviceType.AMAZON)
            else -> throw IllegalStateException("Unable to get platform")
        }

        if (!privacyManager.isAnyFeatureEnabled) {
            return builder.setOptIn(false).setBackgroundEnabled(false).setTags(true, emptySet()).build()
        }

        val shouldSetTags = channelTagRegistrationEnabled
        builder.setTags(shouldSetTags, if (shouldSetTags) tags else null).setIsActive(activityMonitor.isAppForegrounded)

        for (extender in channelRegistrationPayloadExtenders) {
            builder = when (extender) {
                is Suspending -> extender.extend(builder)
                is Blocking -> extender.extend(builder)
            }
        }

        if (privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
            context.packageManager.getPackageInfo(context.packageName, 0)
                ?.versionName
                ?.let { builder.setAppVersion(it) }
            builder.setDeviceModel(Build.MODEL)
            builder.setApiVersion(Build.VERSION.SDK_INT)
        }

        if (privacyManager.isAnyFeatureEnabled) {
            builder.setTimezone(TimeZone.getDefault().id)
            val locale = localeManager.locale
            if (locale.country.isNotEmpty()) {
                builder.setCountry(locale.country)
            }
            if (locale.language.isNotEmpty()) {
                builder.setLanguage(locale.language)
            }
            builder.setSdkVersion(Airship.version)
        }

        if (privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
            val permissions = buildMap {
                permissionsManager.configuredPermissions.forEach { permission ->
                    val status = permissionsManager.checkPermissionStatus(permission)
                    put(permission.value, status.value)
                }
            }

            if (permissions.isNotEmpty()) {
                builder.setPermissions(permissions)
            }
        }

        return builder.build()
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public sealed interface Extender {

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun interface Suspending : Extender {
            public suspend fun extend(builder: ChannelRegistrationPayload.Builder): ChannelRegistrationPayload.Builder
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun interface Blocking : Extender {
            public fun extend(builder: ChannelRegistrationPayload.Builder): ChannelRegistrationPayload.Builder
        }
    }

    public suspend fun Extender.extend(builder: ChannelRegistrationPayload.Builder): ChannelRegistrationPayload.Builder {
        return when (this) {
            is Suspending -> extend(builder)
            is Blocking -> extend(builder)
        }
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
