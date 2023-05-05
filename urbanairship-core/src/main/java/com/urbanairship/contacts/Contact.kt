/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.Size
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.Logger
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.channel.TagGroupsEditor
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.http.RequestException
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.locale.LocaleManager
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Airship contact. A contact is distinct from a channel and represents a "user"
 * within Airship. Contacts may be named and have channels associated with it.
 */
@OpenForTesting
public class Contact internal constructor(
    context: Context,
    private val preferenceDataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val airshipChannel: AirshipChannel,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    activityMonitor: ActivityMonitor,
    private val clock: Clock,
    private val subscriptionListApiClient: SubscriptionListApiClient,
    private val contactManager: ContactManager,
    subscriptionListDispatcher: CoroutineDispatcher
) : AirshipComponent(context, preferenceDataStore) {

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(
        context: Context,
        preferenceDataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        localeManager: LocaleManager,
        audienceOverridesProvider: AudienceOverridesProvider
    ) : this(
        context,
        preferenceDataStore,
        privacyManager,
        airshipChannel,
        audienceOverridesProvider,
        GlobalActivityMonitor.shared(context),
        Clock.DEFAULT_CLOCK,
        SubscriptionListApiClient(config),
        ContactManager(
            preferenceDataStore,
            airshipChannel,
            JobDispatcher.shared(context),
            ContactApiClient(config),
            localeManager,
            audienceOverridesProvider
        ),
        AirshipDispatchers.newSerialDispatcher(),
    )
    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val authTokenProvider: AuthTokenProvider = this.contactManager

    private val subscriptionListCache: CachedValue<Subscriptions> = CachedValue(clock)
    private val scope = CoroutineScope(subscriptionListDispatcher + SupervisorJob())
    private val subscriptionFetchQueue = SerialQueue()

    /**
     * Named user Id updates.
     */
    @JvmSynthetic
    public val namedUserIdFlow: StateFlow<String?> = contactManager.currentNamedUserIdUpdates

    /**
     * Contact conflict listener.
     */
    public var contactConflictListener: ContactConflictListener? = null

    /**
     * The current named user Id.
     */
    public val namedUserId: String?
        get() {
            return contactManager.namedUserId
        }

    private var lastResolvedDate: Long
        get() = preferenceDataStore.getLong(LAST_RESOLVED_DATE_KEY, -1)
        set(newValue) = preferenceDataStore.put(LAST_RESOLVED_DATE_KEY, newValue)

    init {
        migrateNamedUser()
        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(time: Long) {
                if (clock.currentTimeMillis() >= lastResolvedDate + FOREGROUND_RESOLVE_INTERVAL) {
                    if (privacyManager.isContactsEnabled) {
                        contactManager.addOperation(ContactOperation.Resolve)
                    }
                    lastResolvedDate = clock.currentTimeMillis()
                }
            }
        })

        scope.launch {
            for (conflict in contactManager.conflictEvents) {
                contactConflictListener?.onConflict(conflict)
            }
        }

        airshipChannel.addChannelListener {
            if (privacyManager.isContactsEnabled) {
                contactManager.addOperation(ContactOperation.Resolve)
            }
        }

        scope.launch {
            contactManager.contactIdUpdates
                .drop(1)
                .mapNotNull { it?.contactId }
                .distinctUntilChanged()
                .collect {
                    airshipChannel.updateRegistration()
                }
        }

        airshipChannel.addChannelRegistrationPayloadExtender { builder: ChannelRegistrationPayload.Builder ->
            if (privacyManager.isEnabled(PrivacyManager.FEATURE_CONTACTS)) {
                builder.setContactId(contactManager.lastContactId)
            }
            builder
        }

        privacyManager.addListener { checkPrivacyManager() }
        checkPrivacyManager()
        contactManager.isEnabled = true
    }

    private fun checkPrivacyManager() {
        if (privacyManager.isContactsEnabled) {
            contactManager.generateDefaultContactIdIfNotSet()
        } else {
            contactManager.addOperation(ContactOperation.Reset)
        }
    }

    private fun migrateNamedUser() {
        if (privacyManager.isContactsEnabled) {
            val namedUserId = preferenceDataStore.getString(LEGACY_NAMED_USER_ID_KEY, null)
            if (namedUserId == null) {
                contactManager.generateDefaultContactIdIfNotSet()
            } else {
                identify(namedUserId)
                if (privacyManager.isContactsAudienceEnabled) {
                    val attributeJson = preferenceDataStore.getJsonValue(
                        LEGACY_ATTRIBUTE_MUTATION_STORE_KEY
                    )
                    var attributeMutations = AttributeMutation.fromJsonList(attributeJson.optList())
                    attributeMutations = AttributeMutation.collapseMutations(attributeMutations)
                    val tagsJson = preferenceDataStore.getJsonValue(
                        LEGACY_TAG_GROUP_MUTATIONS_KEY
                    )
                    var tagGroupMutations = TagGroupsMutation.fromJsonList(tagsJson.optList())
                    tagGroupMutations = TagGroupsMutation.collapseMutations(tagGroupMutations)
                    if (attributeMutations.isNotEmpty() || tagGroupMutations.isNotEmpty()) {
                        val update =
                            ContactOperation.Update(tagGroupMutations, attributeMutations)
                        contactManager.addOperation(update)
                    }
                }
            }
        }

        preferenceDataStore.remove(LEGACY_TAG_GROUP_MUTATIONS_KEY)
        preferenceDataStore.remove(LEGACY_ATTRIBUTE_MUTATION_STORE_KEY)
        preferenceDataStore.remove(LEGACY_NAMED_USER_ID_KEY)
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    override fun getComponentGroup(): Int {
        return AirshipComponentGroups.CONTACT
    }

    /**
     * @param isEnabled `true` if the component is enabled, otherwise `false`.
     * @hide
     */
    override fun onComponentEnableChange(isEnabled: Boolean) {
        super.onComponentEnableChange(isEnabled)
        if (contactManager.isEnabled != isEnabled) {
            contactManager.isEnabled = isEnabled
        }
    }

    /**
     * Associates the contact with the given named user identifier.
     *
     * @param externalId The channel's identifier.
     */
    public fun identify(@Size(min = 1, max = 128) externalId: String) {
        if (!privacyManager.isContactsEnabled) {
            Logger.d { "Contacts is disabled, ignoring contact identifying." }
            return
        }
        contactManager.addOperation(ContactOperation.Identify(externalId))
    }

    /**
     * Disassociate the channel from its current contact, and create a new
     * un-named contact.
     */
    public fun reset() {
        if (!privacyManager.isContactsEnabled) {
            Logger.d { "Contacts is disabled, ignoring contact reset." }
            return
        }
        contactManager.addOperation(ContactOperation.Reset)
    }

    /**
     * Edit the tags associated with this Contact.
     *
     * @return a [TagGroupsEditor].
     */
    public fun editTagGroups(): TagGroupsEditor {
        return object : TagGroupsEditor() {
            override fun onApply(collapsedMutations: List<TagGroupsMutation>) {
                super.onApply(collapsedMutations)
                if (!privacyManager.isContactsAudienceEnabled) {
                    Logger.w { "Ignoring contact tag edits while contacts and/or tags and attributes are disabled." }
                    return
                }

                if (collapsedMutations.isEmpty()) {
                    return
                }

                contactManager.addOperation(ContactOperation.Update(tags = collapsedMutations))
            }
        }
    }

    /**
     * Registers an Email channel.
     *
     * @param address The Email address to register.
     * @param options An EmailRegistrationOptions object that defines registration options.
     */
    public fun registerEmail(address: String, options: EmailRegistrationOptions) {
        if (!privacyManager.isContactsEnabled) {
            Logger.w { "Ignoring Email registration while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.RegisterEmail(address, options))
    }

    /**
     * Registers a Sms channel.
     *
     * @param msisdn The Mobile Station number to register.
     * @param options An SmsRegistrationObject object that defines registration options.
     */
    public fun registerSms(msisdn: String, options: SmsRegistrationOptions) {
        if (!privacyManager.isContactsEnabled) {
            Logger.w { "Ignoring SMS registration while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.RegisterSms(msisdn, options))
    }

    /**
     * Registers an Open channel.
     *
     * @param address The address to register.
     * @param options An SmsRegistrationObject object that defines registration options.
     */
    public fun registerOpenChannel(address: String, options: OpenChannelRegistrationOptions) {
        if (!privacyManager.isContactsEnabled) {
            Logger.w { "Ignoring open channel registration while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.RegisterOpen(address, options))
    }

    /**
     * Associates a channel to the contact.
     *
     * @param channelId The channel Id.
     * @param channelType The channel type.
     */
    public fun associateChannel(channelId: String, channelType: ChannelType) {
        if (!privacyManager.isContactsEnabled) {
            Logger.w { "Ignoring associate channel request while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.AssociateChannel(channelId, channelType))
    }

    /**
     * Edit the attributes associated with this Contact.
     *
     * @return An [AttributeEditor].
     */
    public fun editAttributes(): AttributeEditor {
        return object : AttributeEditor(clock) {
            override fun onApply(collapsedMutations: List<AttributeMutation>) {
                if (!privacyManager.isContactsAudienceEnabled) {
                    Logger.warn("Contact - Ignoring tag edits while contacts and/or tags and attributes are disabled.")
                    return
                }

                if (collapsedMutations.isEmpty()) {
                    return
                }

                contactManager.addOperation(ContactOperation.Update(attributes = collapsedMutations))
            }
        }
    }

    /**
     * Edits the subscription lists associated with this Contact.
     *
     * @return An [ScopedSubscriptionListEditor].
     */
    public fun editSubscriptionLists(): ScopedSubscriptionListEditor {
        return object : ScopedSubscriptionListEditor(clock) {
            override fun onApply(mutations: List<ScopedSubscriptionListMutation>) {
                if (!privacyManager.isContactsAudienceEnabled) {
                    Logger.warn("Contact - Ignoring subscription list edits while contacts and/or tags and attributes are disabled.")
                    return
                }

                if (mutations.isEmpty()) {
                    return
                }

                contactManager.addOperation(ContactOperation.Update(subscriptions = mutations))
            }
        }
    }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        return if (ACTION_UPDATE_CONTACT == jobInfo.action) {
            val result = runBlocking { contactManager.performNextOperation() }
            return if (result) JobResult.SUCCESS else JobResult.FAILURE
        } else {
            JobResult.SUCCESS
        }
    }

    @JvmSynthetic
    public suspend fun fetchSubscriptionLists(): Result<Map<String, Set<Scope>>> {
        if (!privacyManager.isContactsAudienceEnabled) {
            return Result.failure(
                IllegalStateException("Unable to fetch subscriptions when FEATURE_TAGS_AND_ATTRIBUTES or FEATURE_CONTACTS are disabled")
            )
        }

        val contactId = contactManager.stableContactId()

        // Get the subscription lists from the in-memory cache, if available.
        val result = fetchContactSubscriptionList(contactId)
        val subscriptions = result.getOrNull()
            ?.toMutableMap()
            ?.mapValues { it.value.toMutableSet() }

        if (result.isFailure || subscriptions == null) {
            return result
        }

        audienceOverridesProvider.contactOverrides(contactId).apply {
            this.subscriptions?.forEach { mutation ->
                mutation.apply(subscriptions)
            }
        }

        return Result.success(subscriptions)
    }

    /**
     * Returns the current set of subscription lists for the current contact.
     *
     *
     * An empty set indicates that this contact is not subscribed to any lists.
     *
     * @return A [PendingResult] of the current set of subscription lists.
     */
    public fun fetchSubscriptionListsPendingResult(): PendingResult<Map<String, Set<Scope>>?> {
        val pendingResult = PendingResult<Map<String, Set<Scope>>?>()
        scope.launch {
            pendingResult.result = fetchSubscriptionLists().getOrNull()
        }
        return pendingResult
    }

    /**
     * Returns the current set of subscription lists for the current contact.
     *
     *
     * An empty set indicates that this contact is not subscribed to any lists.
     *
     * @return A [PendingResult] of the current set of subscription lists.
     */
    @Deprecated("Use fetchSubscriptionListsPendingResult() instead")
    public fun getSubscriptionLists(): PendingResult<Map<String, Set<Scope>>?> {
        val pendingResult = PendingResult<Map<String, Set<Scope>>?>()
        scope.launch {
            pendingResult.result = fetchSubscriptionLists().getOrNull()
        }
        return pendingResult
    }

    private suspend fun fetchContactSubscriptionList(contactId: String): Result<Map<String, Set<Scope>>> {
        return subscriptionFetchQueue.run {
            val cached = subscriptionListCache.get()
            if (cached != null && cached.contactId == contactId) {
                return@run Result.success(cached.subscriptions)
            }

            val response = subscriptionListApiClient.getSubscriptionLists(contactId)
            if (response.isSuccessful && response.value != null) {
                subscriptionListCache.set(
                    Subscriptions(contactId, response.value),
                    clock.currentTimeMillis() + SUBSCRIPTION_CACHE_LIFETIME_MS
                )
                return@run Result.success(response.value)
            }

            return@run Result.failure(RequestException("Failed to fetch subscription lists with status: ${response.status}"))
        }
    }

    internal companion object {

        @VisibleForTesting
        internal val LEGACY_NAMED_USER_ID_KEY = "com.urbanairship.nameduser.NAMED_USER_ID_KEY"

        @VisibleForTesting
        internal val LEGACY_ATTRIBUTE_MUTATION_STORE_KEY = "com.urbanairship.nameduser.ATTRIBUTE_MUTATION_STORE_KEY"

        @VisibleForTesting
        internal val LEGACY_TAG_GROUP_MUTATIONS_KEY = "com.urbanairship.nameduser.PENDING_TAG_GROUP_MUTATIONS_KEY"

        @VisibleForTesting
        internal val ACTION_UPDATE_CONTACT = "ACTION_UPDATE_CONTACT"

        /**
         * Max age for the contact subscription listing cache.
         */
        private const val SUBSCRIPTION_CACHE_LIFETIME_MS: Long = 10 * 60 * 1000 // 10M
        private const val LAST_RESOLVED_DATE_KEY = "com.urbanairship.contacts.LAST_RESOLVED_DATE_KEY"
        private const val FOREGROUND_RESOLVE_INTERVAL: Long = 24 * 60 * 60 * 1000 // 24 hours
    }

    private data class Subscriptions(
        val contactId: String,
        val subscriptions: Map<String, Set<Scope>>
    )
}

private val PrivacyManager.isContactsEnabled get() = this.isEnabled(PrivacyManager.FEATURE_CONTACTS)
private val PrivacyManager.isContactsAudienceEnabled get() = this.isEnabled(PrivacyManager.FEATURE_CONTACTS, PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
