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
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipSmsValidator
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SmsValidationHandler
import com.urbanairship.channel.SmsValidator
import com.urbanairship.channel.TagGroupsEditor
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.http.RequestException
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.locale.LocaleManager
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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
    private val config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val airshipChannel: AirshipChannel,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    activityMonitor: ActivityMonitor,
    private val clock: Clock,
    private val subscriptionListApiClient: SubscriptionListApiClient,
    private val contactManager: ContactManager,
    private val smsValidator: SmsValidator,
    pushManager: PushManager,
    contactChannelsProvider: ContactChannelsProvider = ContactChannelsProvider(
        config,
        audienceOverridesProvider,
        contactManager.contactIdUpdates
    ),
    subscriptionListDispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AirshipComponent(context, preferenceDataStore) {
    internal constructor(
        context: Context,
        preferenceDataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        localeManager: LocaleManager,
        audienceOverridesProvider: AudienceOverridesProvider,
        pushManager: PushManager
    ) : this(
        context,
        preferenceDataStore,
        config,
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
        AirshipSmsValidator(config),
        pushManager
    )

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val authTokenProvider: AuthTokenProvider = this.contactManager

    private val subscriptionListCache: CachedValue<Subscriptions> = CachedValue(clock)
    private val subscriptionsScope = CoroutineScope(subscriptionListDispatcher + SupervisorJob())
    private val subscriptionFetchQueue = SerialQueue()

    /**
     * Named user Id updates.
     */
    @JvmSynthetic
    public val namedUserIdFlow: StateFlow<String?> = contactManager.currentNamedUserIdUpdates

    internal val contactIdUpdateFlow: Flow<ContactIdUpdate?> = contactManager.contactIdUpdates

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

    /** The foreground resolve interval from remote config, or the default [FOREGROUND_INTERVAL] if not available. */
    private val foregroundResolveInterval: Long
        get() = config.remoteConfig.contactConfig?.foregroundIntervalMs ?: FOREGROUND_INTERVAL

    /** The CRA max age from remote config, or the default [CRA_MAX_AGE] if not available. */
    private val channelRegistrationMaxResolveAge: Long
        get() = config.remoteConfig.contactConfig?.channelRegistrationMaxResolveAgeMs ?: CRA_MAX_AGE

    internal val currentContactIdUpdate: ContactIdUpdate?
        get() = contactManager.currentContactIdUpdate

    /**
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val lastContactId: String?
        get() = contactManager.lastContactId

    internal suspend fun stableContactInfo(): StableContactInfo {
        return contactManager.stableContactIdUpdate().toContactInfo()
    }

    private suspend fun stableVerifiedContactId(): String {
        val stable = contactManager.stableContactIdUpdate()
        val age = this.clock.currentTimeMillis() - stable.resolveDateMs

        if (age <= channelRegistrationMaxResolveAge) {
            return stable.contactId
        }

        val now = this.clock.currentTimeMillis()
        contactManager.addOperation(ContactOperation.Verify(now))
        return contactManager.stableContactIdUpdate(now).contactId
    }

    private val channelExtender = AirshipChannel.Extender.Suspending { builder ->
        if (privacyManager.isEnabled(PrivacyManager.Feature.CONTACTS)) {
            if (airshipChannel.id != null) {
                builder.setContactId(stableVerifiedContactId())
            } else {
                builder.setContactId(contactManager.lastContactId)
            }
        }
        builder
    }

    init {
        migrateNamedUser()
        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(time: Long) {
                if (clock.currentTimeMillis() >= lastResolvedDate + foregroundResolveInterval) {
                    if (privacyManager.isContactsEnabled) {
                        contactManager.addOperation(ContactOperation.Resolve)
                    }
                    lastResolvedDate = clock.currentTimeMillis()
                }

                contactChannelsProvider.refresh()
            }
        })

        pushManager.addInternalPushListener { message, _ ->
            if (message.containsKey(CONTACT_UPDATE_PUSH_KEY)) {
                contactChannelsProvider.refresh()
            }
        }

        subscriptionsScope.launch {
            for (conflict in contactManager.conflictEvents) {
                contactConflictListener?.onConflict(conflict)
            }
        }

        airshipChannel.addChannelListener {
            if (privacyManager.isContactsEnabled) {
                contactManager.addOperation(ContactOperation.Resolve)
            }
        }

        subscriptionsScope.launch {
            contactManager.contactIdUpdates
                .drop(1)
                .mapNotNull { it?.contactId }
                .distinctUntilChanged()
                .collect {
                    airshipChannel.updateRegistration()
                }
        }

        airshipChannel.addChannelRegistrationPayloadExtender(channelExtender)

        privacyManager.addListener(object : PrivacyManager.Listener {
            override fun onEnabledFeaturesChanged() = checkPrivacyManager()
        })

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
     * Associates the contact with the given named user identifier.
     *
     * @param externalId The channel's identifier.
     */
    public fun identify(@Size(min = 1, max = 128) externalId: String) {
        if (!privacyManager.isContactsEnabled) {
            UALog.d { "Contacts is disabled, ignoring contact identifying." }
            return
        }
        contactManager.addOperation(ContactOperation.Identify(externalId))
    }

    public fun notifyRemoteLogin() {
        if (!privacyManager.isContactsEnabled) {
            UALog.d { "Contacts is disabled, ignoring contact remote-login request." }
            return
        }

        contactManager.addOperation(
                ContactOperation.Verify(
                        dateMs = this.clock.currentTimeMillis(),
                        required = true
                )
        )
    }

    /**
     * Disassociate the channel from its current contact, and create a new
     * un-named contact.
     */
    public fun reset() {
        if (!privacyManager.isContactsEnabled) {
            UALog.d { "Contacts is disabled, ignoring contact reset." }
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
                    UALog.w { "Ignoring contact tag edits while contacts and/or tags and attributes are disabled." }
                    return
                }

                if (collapsedMutations.isEmpty()) {
                    return
                }

                contactManager.addOperation(ContactOperation.Update(tags = collapsedMutations))
                audienceOverridesProvider.notifyPendingChanged()
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
            UALog.w { "Ignoring Email registration while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.RegisterEmail(address, options))
        audienceOverridesProvider.notifyPendingChanged()
    }

    /**
     * Registers a Sms channel.
     *
     * @param msisdn The Mobile Station number to register.
     * @param options An SmsRegistrationObject object that defines registration options.
     */
    public fun registerSms(msisdn: String, options: SmsRegistrationOptions) {
        if (!privacyManager.isContactsEnabled) {
            UALog.w { "Ignoring SMS registration while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.RegisterSms(msisdn, options))
        audienceOverridesProvider.notifyPendingChanged()
    }

    /**
     * Registers an Open channel.
     *
     * @param address The address to register.
     * @param options An SmsRegistrationObject object that defines registration options.
     */
    public fun registerOpenChannel(address: String, options: OpenChannelRegistrationOptions) {
        if (!privacyManager.isContactsEnabled) {
            UALog.w { "Ignoring open channel registration while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.RegisterOpen(address, options))
        audienceOverridesProvider.notifyPendingChanged()
    }

    /**
     * Associates a channel to the contact.
     *
     * @param channelId The channel Id.
     * @param channelType The channel type.
     */
    public fun associateChannel(channelId: String, channelType: ChannelType) {
        if (!privacyManager.isContactsEnabled) {
            UALog.w { "Ignoring associate channel request while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.AssociateChannel(channelId, channelType))
        audienceOverridesProvider.notifyPendingChanged()
    }

    /**
     * Disassociates the contact channel.
     * @param contactChannel The channel.
     * @param optOut If the channel should also be opted out.
     */
    public fun disassociateChannel(contactChannel: ContactChannel, optOut: Boolean = true) {
        if (!privacyManager.isContactsEnabled) {
            UALog.w { "Ignoring disassociate channel request while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.DisassociateChannel(contactChannel, optOut))
        audienceOverridesProvider.notifyPendingChanged()
    }

    /**
     * Resends double-opt in for the given contact channel.
     * @param contactChannel The channel.
     */
    public fun resendDoubleOptIn(contactChannel: ContactChannel) {
        if (!privacyManager.isContactsEnabled) {
            UALog.w { "Ignoring resend double opt-in request while contacts are disabled." }
            return
        }
        contactManager.addOperation(ContactOperation.Resend(contactChannel))
    }

    /**
     * Validates an SMS number.
     *
     * @param msisdn The MSISDN (phone number) to validate.
     * @param sender The identifier given to the sender of the SMS message.
     * @return `true` if the MSISDN and sender combination are valid, otherwise `false`.
     */
    public suspend fun validateSms(msisdn: String, sender: String): Boolean {
        return smsValidator.validateSms(msisdn, sender)
    }

    /**
     * Sets the SMS validation handler, to allow overriding of the default Airship validation.
     *
     * @param handler An implementation of [SmsValidationHandler], or `null` to remove the existing handler.
     */
    public fun setSmsValidationHandler(handler: SmsValidationHandler?) {
        smsValidator.handler = handler
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
                    UALog.w("Contact - Ignoring tag edits while contacts and/or tags and attributes are disabled.")
                    return
                }

                if (collapsedMutations.isEmpty()) {
                    return
                }

                contactManager.addOperation(ContactOperation.Update(attributes = collapsedMutations))
                audienceOverridesProvider.notifyPendingChanged()
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
                    UALog.w("Contact - Ignoring subscription list edits while contacts and/or tags and attributes are disabled.")
                    return
                }

                if (mutations.isEmpty()) {
                    return
                }

                contactManager.addOperation(ContactOperation.Update(subscriptions = mutations))
                audienceOverridesProvider.notifyPendingChanged()
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

        val contactId = stableContactInfo().contactId

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

    public val channelContacts: Flow<Result<List<ContactChannel>>> = contactChannelsProvider.contactChannels

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
        subscriptionsScope.launch {
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
        subscriptionsScope.launch {
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

        private const val LAST_RESOLVED_DATE_KEY = "com.urbanairship.contacts.LAST_RESOLVED_DATE_KEY"

        /** Max age for the contact subscription listing cache. */
        private val SUBSCRIPTION_CACHE_LIFETIME_MS = TimeUnit.MINUTES.toMillis(10)

        /** Default foreground refresh interval. */
        private val FOREGROUND_INTERVAL = TimeUnit.MINUTES.toMillis(60)

        /** Default CRA max age. */
        private val CRA_MAX_AGE = TimeUnit.MINUTES.toMillis(10)

        private val CONTACT_UPDATE_PUSH_KEY = "com.urbanairship.contact.update"

    }

    private data class Subscriptions(
        val contactId: String,
        val subscriptions: Map<String, Set<Scope>>
    )
}

private val PrivacyManager.isContactsEnabled get() = this.isEnabled(PrivacyManager.Feature.CONTACTS)
private val PrivacyManager.isContactsAudienceEnabled get() = this.isEnabled(PrivacyManager.Feature.CONTACTS, PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
