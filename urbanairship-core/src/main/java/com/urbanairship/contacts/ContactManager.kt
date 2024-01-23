/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import androidx.annotation.OpenForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.http.AuthToken
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.http.RequestException
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobInfo.ConflictStrategy
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonList
import com.urbanairship.json.tryParse
import com.urbanairship.locale.LocaleManager
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@OpenForTesting
internal class ContactManager(
    private val preferenceDataStore: PreferenceDataStore,
    private val channel: AirshipChannel,
    private val jobDispatcher: JobDispatcher,
    private val contactApiClient: ContactApiClient,
    private val localeManager: LocaleManager,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AuthTokenProvider {

    private val identifyOperationQueue = SerialQueue()
    private val operationLock: ReentrantLock = ReentrantLock()
    private val identityLock: ReentrantLock = ReentrantLock()
    private var lastIdentifyTimeMs: Long = 0

    private val _contactIdUpdates: MutableStateFlow<ContactIdUpdate?> = MutableStateFlow(null)
    val contactIdUpdates: Flow<ContactIdUpdate?> = _contactIdUpdates.asStateFlow()

    private val _currentNamedUserIdUpdates: MutableStateFlow<String?> = MutableStateFlow(null)
    val currentNamedUserIdUpdates: StateFlow<String?> = _currentNamedUserIdUpdates.asStateFlow()

    val conflictEvents: Channel<ConflictEvent> = Channel(Channel.UNLIMITED)

    private val cachedAuthToken: CachedValue<AuthToken> = CachedValue()

    @Volatile
    internal var isEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                dispatchContactUpdateJob()
            }
        }

    private var _operations: List<OperationEntry>? = null
    private var operations: List<OperationEntry>
        get() {
            return operationLock.withLock {
                val result = _operations ?: preferenceDataStore.optJsonValue(OPERATIONS_KEY)
                    ?.tryParse { json ->
                        json.requireList().map { OperationEntry(it) }
                    } ?: emptyList()

                _operations = result
                result
            }
        }
        set(newValue) {
            operationLock.withLock {
                _operations = newValue
                preferenceDataStore.put(OPERATIONS_KEY, newValue.toJsonList())
            }
        }

    private val hasAnonDate: Boolean
        get() {
            return lastContactIdentity?.isAnonymous == true && anonData?.isEmpty == false
        }

    private var anonData: ContactData?
        get() = preferenceDataStore.optJsonValue(ANON_CONTACT_DATA_KEY)?.tryParse { json ->
            ContactData(json)
        }
        set(newValue) = preferenceDataStore.put(ANON_CONTACT_DATA_KEY, newValue)

    private var _identity: ContactIdentity? = null
    private var lastContactIdentity: ContactIdentity?
        get() {
            return identityLock.withLock {
                val result =
                    _identity ?: preferenceDataStore.optJsonValue(LAST_CONTACT_IDENTITY_KEY)
                        ?.tryParse { ContactIdentity(it) }
                _identity = result
                result
            }
        }
        set(newValue) {
            identityLock.withLock {
                _identity = newValue
                preferenceDataStore.put(LAST_CONTACT_IDENTITY_KEY, newValue)
            }
        }
    private val possiblyOrphanedContactId: String?
        get() = lastContactIdentity?.let {
                if (it.isAnonymous && (anonData?.associatedChannels?.isEmpty() != false)) {
                    it.contactId
                } else {
                    null
                }
            }

    internal val currentContactIdUpdate: ContactIdUpdate?
        get() {
            val lastIdentity = this.lastContactIdentity ?: return null
            val isStable = this.operations.firstOrNull {
                when (it.operation) {
                    is ContactOperation.Reset -> true
                    is ContactOperation.Verify -> {
                        it.operation.required
                    }
                    is ContactOperation.Identify -> {
                        it.operation.identifier != lastIdentity.namedUserId
                    }
                    else -> false
                }
            } == null

            return ContactIdUpdate(
                    contactId = lastIdentity.contactId,
                    isStable = isStable,
                    resolveDateMs = lastIdentity.resolveDateMs ?: 0
            )
        }

    internal val namedUserId: String?
        get() {
            val namedUserId = lastContactIdentity?.namedUserId

            val operation = this.operations.reversed().firstOrNull {
                it.operation is ContactOperation.Identify || it.operation is ContactOperation.Reset
            } ?: return namedUserId

            return when (operation.operation) {
                is ContactOperation.Reset -> null
                is ContactOperation.Identify -> operation.operation.identifier
                else -> namedUserId
            }
        }

    val lastContactId: String?
        get() {
            return this.lastContactIdentity?.contactId
        }

    init {
        // Migrate operations -> dated operations
        preferenceDataStore.optJsonValue(OPERATIONS_KEY)?.let { json ->
            if (!preferenceDataStore.isSet(OPERATION_ENTRIES_KEY)) {
                val operations = json.optList()
                    .tryParse(logError = true) { list -> list.map { ContactOperation.fromJson(it) } }
                operations?.map { OperationEntry(clock.currentTimeMillis(), it) }?.let {
                    this.operations = it
                }
            }

            preferenceDataStore.remove(OPERATIONS_KEY)
        }

        audienceOverridesProvider.pendingContactOverridesDelegate = {
            getPendingAudienceOverrides(it)
        }

        audienceOverridesProvider.stableContactIdDelegate = {
            stableContactIdUpdate().contactId
        }

        jobDispatcher.setRateLimit(IDENTITY_RATE_LIMIT, 1, 5, TimeUnit.SECONDS)
        jobDispatcher.setRateLimit(UPDATE_RATE_LIMIT, 1, 500, TimeUnit.MILLISECONDS)

        yieldContactUpdates()
    }

    internal suspend fun stableContactIdUpdate(minResolveDate: Long = 0): ContactIdUpdate {
        return contactIdUpdates.mapNotNull { it }
                .first {
                    it.isStable && it.resolveDateMs >= minResolveDate
                }
    }

    internal fun addOperation(operation: ContactOperation) {
        operationLock.withLock {
            val operations = this.operations.toMutableList()
            operations.add(OperationEntry(clock.currentTimeMillis(), operation))
            this.operations = operations
        }

        dispatchContactUpdateJob()
        yieldContactUpdates()
    }

    override suspend fun fetchToken(identifier: String): Result<String> {
        return withContext(dispatcher) {
            var tokenIfValid = tokenIfValid()
            if (identifier == lastContactIdentity?.contactId && tokenIfValid != null) {
                return@withContext Result.success(tokenIfValid)
            }

            performOperation(ContactOperation.Resolve)
            yieldContactUpdates()

            if (identifier != lastContactIdentity?.contactId) {
                return@withContext Result.failure(RequestException("Stale contact Id"))
            }

            tokenIfValid = tokenIfValid()
            if (tokenIfValid != null) {
                return@withContext Result.success(tokenIfValid)
            }

            return@withContext Result.failure(RequestException("Failed to refresh token"))
        }
    }

    internal fun generateDefaultContactIdIfNotSet() {
        identityLock.withLock {
            if (this.lastContactIdentity == null) {
                this.lastContactIdentity = ContactIdentity(
                        contactId = UUID.randomUUID().toString(),
                        isAnonymous = true,
                        namedUserId = null,
                        resolveDateMs = this.clock.currentTimeMillis()
                )
                addOperation(ContactOperation.Resolve)
            }
        }

        yieldContactUpdates()
    }

    override suspend fun expireToken(token: String) {
        withContext(dispatcher) {
            cachedAuthToken.expireIf { it.token == token }
        }
    }

    // Many threads
    private fun yieldContactUpdates() {
        _currentNamedUserIdUpdates.update { namedUserId }
        _contactIdUpdates.update { currentContactIdUpdate }
    }

    suspend fun performNextOperation(): Boolean {
        return withContext(dispatcher) {
            if (!isEnabled) {
                return@withContext true
            }

            if (operations.isEmpty()) {
                return@withContext true
            }

            if (tokenIfValid() == null) {
                val resolveResult = performOperation(ContactOperation.Resolve)
                yieldContactUpdates()
                if (!resolveResult) {
                    return@withContext false
                }
            }

            clearSkippableOperations()
            yieldContactUpdates()
            val nextOperationGroup = prepareNextOperationGroup() ?: return@withContext true

            return@withContext if (performOperation(nextOperationGroup.merged)) {
                operationLock.withLock {
                    val identifiers = nextOperationGroup.operations.map { it.identifier }
                    operations = operations.filter { !identifiers.contains(it.identifier) }
                    if (operations.isNotEmpty()) {
                        dispatchContactUpdateJob(JobInfo.REPLACE)
                    }
                }
                true
            } else {
                false
            }.also {
                yieldContactUpdates()
            }
        }
    }

    private fun dispatchContactUpdateJob(@ConflictStrategy conflictStrategy: Int = JobInfo.KEEP) {
        if (channel.id.isNullOrEmpty()) {
            return
        }

        if (!isEnabled) {
            return
        }

        val operations = this.operations
        if (operations.isEmpty()) {
            return
        }

        val builder = JobInfo.newBuilder().setAction(Contact.ACTION_UPDATE_CONTACT)
            .setNetworkAccessRequired(true).setAirshipComponent(Contact::class.java)
            .setConflictStrategy(conflictStrategy).addRateLimit(UPDATE_RATE_LIMIT)

        val next = operations.firstOrNull { !isSkippable(it.operation) }?.operation
        if (next is ContactOperation.Reset || next is ContactOperation.Resolve || next is ContactOperation.Reset) {
            builder.addRateLimit(IDENTITY_RATE_LIMIT)
        }

        jobDispatcher.dispatch(builder.build())
    }

    private fun getPendingAudienceOverrides(contactId: String): AudienceOverrides.Contact {
        val currentIdentity = this.lastContactIdentity ?: return AudienceOverrides.Contact()
        val operations = this.operations.map { it.operation }

        if (contactId != currentIdentity.contactId) {
            return AudienceOverrides.Contact()
        }

        val tags: MutableList<TagGroupsMutation> = ArrayList()
        val attributes: MutableList<AttributeMutation> = ArrayList()
        val subscriptions: MutableList<ScopedSubscriptionListMutation> = ArrayList()
        var lastOperationNamedUser: String? = null

        for (operation in operations) {
            // If we are at a reset, the contact ID will change so break
            if (operation is ContactOperation.Reset) {
                break
            }

            // If we have an identify:
            // - not anonymous, break if the named user ID is not a match
            // - is anonymous, break on the second named user mismatch since the first identify could
            //   result in the same contact ID
            if (operation is ContactOperation.Identify) {
                if (!currentIdentity.isAnonymous && operation.identifier != currentIdentity.namedUserId) {
                    break
                }
                if (lastOperationNamedUser != null && lastOperationNamedUser != operation.identifier) {
                    break
                }
                lastOperationNamedUser = operation.identifier
            }

            if (operation is ContactOperation.Update) {
                operation.tags?.let { tags.addAll(it) }
                operation.attributes?.let { attributes.addAll(it) }
                operation.subscriptions?.let { subscriptions.addAll(it) }
            }
        }

        return AudienceOverrides.Contact(tags, attributes, subscriptions)
    }

    private fun prepareNextOperationGroup(): OperationGroup? {
        val operations = this.operations.toMutableList()

        if (operations.isEmpty()) {
            return null
        }

        val next = operations.removeFirst()
        when (next.operation) {
            is ContactOperation.Update -> {
                val group = mutableListOf(next)
                val mergedTags = mutableListOf<TagGroupsMutation>()
                val mergedAttributes = mutableListOf<AttributeMutation>()
                val mergedSubLists = mutableListOf<ScopedSubscriptionListMutation>()

                next.operation.tags?.let { mergedTags.addAll(it) }
                next.operation.attributes?.let { mergedAttributes.addAll(it) }
                next.operation.subscriptions?.let { mergedSubLists.addAll(it) }

                for (nextNext in operations) {
                    if (nextNext.operation is ContactOperation.Update) {
                        nextNext.operation.tags?.let { mergedTags.addAll(it) }
                        nextNext.operation.attributes?.let { mergedAttributes.addAll(it) }
                        nextNext.operation.subscriptions?.let { mergedSubLists.addAll(it) }
                        group.add(nextNext)
                    } else {
                        break
                    }
                }

                val merged = ContactOperation.Update(
                    TagGroupsMutation.collapseMutations(mergedTags),
                    AttributeMutation.collapseMutations(mergedAttributes),
                    ScopedSubscriptionListMutation.collapseMutations(mergedSubLists)
                )

                return OperationGroup(group, merged)
            }

            is ContactOperation.Reset, is ContactOperation.Identify -> {
                // A series of resets and identifies can be skipped and only the last reset or identify
                // can be performed if we do not have any anon data.
                if (this.hasAnonDate) {
                    return OperationGroup(listOf(next), next.operation)
                }

                val group = mutableListOf(next)

                for (nextNext in operations) {
                    if (nextNext.operation is ContactOperation.Reset || nextNext.operation is ContactOperation.Identify) {
                        group.add(nextNext)
                    } else {
                        break
                    }
                }
                return OperationGroup(group, group.last().operation)
            }

            else -> {
                return OperationGroup(listOf(next), next.operation)
            }
        }
    }

    private fun tokenIfValid(): String? {
        val auth = cachedAuthToken.get()
        if (auth == null || auth.identifier != lastContactId) {
            return null
        }
        if (clock.currentTimeMillis() > auth.expirationDateMillis - 30000) {
            return null
        }
        return auth.token
    }

    private fun clearSkippableOperations() {
        operationLock.withLock {
            val operations = this.operations.filter { !isSkippable(it.operation) }
            this.operations = operations
        }
    }

    private fun isSkippable(operation: ContactOperation): Boolean {
        return when (operation) {
            is ContactOperation.Update -> {
                 operation.attributes.isNullOrEmpty() && operation.tags.isNullOrEmpty() && operation.subscriptions.isNullOrEmpty()
            }

            is ContactOperation.Identify -> {
                return operation.identifier == this.lastContactIdentity?.namedUserId && tokenIfValid() != null
            }

            is ContactOperation.Reset -> {
                return lastContactIdentity?.isAnonymous == true && !hasAnonDate && tokenIfValid() != null
            }

            is ContactOperation.Resolve -> {
                return tokenIfValid() != null
            }

            is ContactOperation.Verify -> {
                val lastResolveDateMs = lastContactIdentity?.resolveDateMs
                return lastResolveDateMs != null && operation.dateMs <= lastResolveDateMs
            }

            else -> false
        }
    }

    private suspend fun performOperation(operation: ContactOperation): Boolean {
        if (isSkippable(operation)) {
            return true
        }

        val channelId = channel.id ?: return false

        return when (operation) {
            is ContactOperation.Reset -> performReset(channelId)
            is ContactOperation.Identify -> performIdentify(channelId, operation)
            is ContactOperation.Resolve -> performResolve(channelId)
            is ContactOperation.Verify -> performResolve(channelId)
            is ContactOperation.Update -> performUpdate(operation)
            is ContactOperation.AssociateChannel -> performAssociateChannel(operation)
            is ContactOperation.RegisterEmail -> performRegisterEmail(operation)
            is ContactOperation.RegisterSms -> performRegisterSms(operation)
            is ContactOperation.RegisterOpen -> performRegisterOpen(operation)
            is ContactOperation.OptinCheck -> performOptinCheck(channelId)
        }
    }

    private suspend fun performReset(channelId: String): Boolean = doIdentify {
        val response = contactApiClient.reset(channelId, possiblyOrphanedContactId)

        if (response.value != null && response.isSuccessful) {
            updateContactIdentity(response.value, null, false)
        }

        response.isSuccessful || response.isClientError
    }

    private suspend fun performIdentify(channelId: String, operation: ContactOperation.Identify): Boolean = doIdentify {
        val response = contactApiClient.identify(
            channelId,
            lastContactIdentity?.contactId,
            operation.identifier,
            possiblyOrphanedContactId
        )

        if (response.value != null && response.isSuccessful) {
            updateContactIdentity(response.value, operation.identifier, false)
        }

        response.isSuccessful || response.isClientError
    }

    private suspend fun performResolve(channelId: String): Boolean = doIdentify {
        val response = contactApiClient.resolve(
            channelId,
            lastContactIdentity?.contactId,
            possiblyOrphanedContactId
        )

        if (response.value != null && response.isSuccessful) {
            updateContactIdentity(response.value, null, true)
        }

        response.isSuccessful || response.isClientError
    }

    private suspend fun performUpdate(operation: ContactOperation.Update): Boolean {
        val contactId = this.lastContactId ?: return false

        val response = contactApiClient.update(
            contactId,
            operation.tags,
            operation.attributes,
            operation.subscriptions
        )

        if (response.isSuccessful) {
            audienceOverridesProvider.recordContactUpdate(
                contactId,
                operation.tags,
                operation.attributes,
                operation.subscriptions
            )
            contactUpdated(contactId, operation)
        }

        return response.isSuccessful || response.isClientError
    }

    private suspend fun doIdentify(operation: suspend () -> Boolean): Boolean {
        return identifyOperationQueue.run {
            val rateLimit = lastIdentifyTimeMs + TimeUnit.SECONDS.toMillis(5) - Clock.DEFAULT_CLOCK.currentTimeMillis()
            if (rateLimit > 0) {
                delay(rateLimit)
            }

            delay(200)

            val result = operation()
            lastIdentifyTimeMs = Clock.DEFAULT_CLOCK.currentTimeMillis()
            result
        }
    }

    private suspend fun performAssociateChannel(operation: ContactOperation.AssociateChannel): Boolean {
        val contactId = this.lastContactId ?: return false

        val response = contactApiClient.associatedChannel(
            contactId, operation.channelId, operation.channelType
        )

        if (response.value != null && response.isSuccessful) {
            contactUpdated(contactId, associatedChannel = response.value)
        }

        return response.isSuccessful || response.isClientError
    }

    private suspend fun performRegisterSms(operation: ContactOperation.RegisterSms): Boolean {
        val contactId = this.lastContactId ?: return false

        val response = contactApiClient.registerSms(
            contactId = contactId,
            msisdn = operation.msisdn,
            options = operation.options,
            locale = localeManager.locale
        )

        if (response.value != null && response.isSuccessful) {
            contactUpdated(contactId, associatedChannel = response.value)
        }

        return response.isSuccessful || response.isClientError
    }

    private suspend fun performRegisterEmail(operation: ContactOperation.RegisterEmail): Boolean {
        val contactId = this.lastContactId ?: return false

        val response = contactApiClient.registerEmail(
            contactId = contactId,
            emailAddress = operation.emailAddress,
            options = operation.options,
            locale = localeManager.locale
        )

        if (response.value != null && response.isSuccessful) {
            contactUpdated(contactId, associatedChannel = response.value)
        }

        return response.isSuccessful || response.isClientError
    }

    private suspend fun performRegisterOpen(operation: ContactOperation.RegisterOpen): Boolean {
        val contactId = this.lastContactId ?: return false

        val response = contactApiClient.registerOpen(
            contactId = contactId,
            address = operation.address,
            options = operation.options,
            locale = localeManager.locale
        )

        if (response.value != null && response.isSuccessful) {
            contactUpdated(contactId, associatedChannel = response.value)
        }

        return response.isSuccessful || response.isClientError
    }

    private suspend fun performOptinCheck(channelId: String): Boolean {
        // TODO complete this method with correct requests
        val response = contactApiClient.performOptinCheck(channelId)
        return true
    }

    private fun updateContactIdentity(
        result: ContactApiClient.IdentityResult,
        namedUserId: String?,
        isResolve: Boolean
    ) {
        identityLock.withLock {
            val auth = AuthToken(
                result.contactId, result.token, result.tokenExpiryDateMs
            )
            cachedAuthToken.set(auth, result.tokenExpiryDateMs)

            val resolvedNamedUser = if (result.contactId == lastContactIdentity?.contactId) {
                namedUserId ?: lastContactIdentity?.namedUserId
            } else {
                namedUserId
            }

            val contactIdentity = ContactIdentity(
                    contactId = result.contactId,
                    isAnonymous = result.isAnonymous,
                    namedUserId = resolvedNamedUser,
                    resolveDateMs = this.clock.currentTimeMillis()
            )

            // Conflict
            if (this.lastContactIdentity != null && contactIdentity.contactId != this.lastContactIdentity?.contactId && this.hasAnonDate) {
                val anonData = requireNotNull(this.anonData)
                conflictEvents.trySend(
                    ConflictEvent(
                        tagGroups = anonData.tagGroups,
                        subscriptionLists = anonData.subscriptionLists,
                        attributes = anonData.attributes,
                        associatedChannels = anonData.associatedChannels,
                        conflictingNameUserId = namedUserId
                    )
                )
                this.anonData = null
            }

            if (!contactIdentity.isAnonymous) {
                this.anonData = null
            }

            // If we have a resolve that returns a new contactID then it means
            // it was changed server side. Clear any pending operations that are
            // older than the resolve date.
            if (this.lastContactIdentity != null && contactIdentity.contactId != this.lastContactIdentity?.contactId && isResolve) {
                this.operationLock.withLock {
                    this.operations = this.operations.filter { operation ->
                        result.channelAssociatedDateMs < operation.dateMillis
                    }
                }
            }

            this.lastContactIdentity = contactIdentity
        }
    }

    private fun contactUpdated(
        contactId: String,
        updateOperation: ContactOperation.Update? = null,
        associatedChannel: AssociatedChannel? = null,
    ) {
        if (contactId != this.lastContactIdentity?.contactId) {
            return
        }

        if (this.lastContactIdentity?.isAnonymous == true) {
            val attributes: MutableMap<String, JsonValue> = mutableMapOf()
            val tagGroups: MutableMap<String, MutableSet<String>> = mutableMapOf()
            val channels: MutableList<AssociatedChannel> = mutableListOf()
            val subscriptionLists: MutableMap<String, MutableSet<Scope>> = mutableMapOf()
            val anonData = this.anonData
            if (anonData != null) {
                attributes.putAll(anonData.attributes)
                anonData.tagGroups.forEach {
                    tagGroups.getOrPut(it.key) { mutableSetOf() }.addAll(it.value)
                }

                channels.addAll(anonData.associatedChannels)

                anonData.subscriptionLists.forEach {
                    subscriptionLists.getOrPut(it.key) { mutableSetOf() }.addAll(it.value)
                }
            }

            if (updateOperation != null) {
                updateOperation.attributes?.forEach { mutation ->
                    when (mutation.action) {
                        AttributeMutation.ATTRIBUTE_ACTION_SET -> attributes[mutation.name] =
                            mutation.value
                        AttributeMutation.ATTRIBUTE_ACTION_REMOVE -> attributes.remove(mutation.name)
                    }
                }

                updateOperation.tags?.forEach { mutation ->
                    mutation.apply(tagGroups)
                }

                updateOperation.subscriptions?.forEach { mutation ->
                    mutation.apply(subscriptionLists)
                }
            }

            if (associatedChannel != null) {
                channels.add(associatedChannel)
            }

            this.anonData = ContactData(tagGroups, attributes, subscriptionLists, channels)
        }
    }

    private data class OperationGroup(val operations: List<OperationEntry>, val merged: ContactOperation)

    private data class OperationEntry(
        val dateMillis: Long,
        val operation: ContactOperation,
        val identifier: String = UUID.randomUUID().toString()
    ) : JsonSerializable {
        constructor(jsonValue: JsonValue) : this(
            jsonValue.requireMap().requireField("timestamp"),
            ContactOperation.fromJson(jsonValue.requireMap().require("operation")),
            jsonValue.requireMap().requireField("identifier")
        )

        override fun toJsonValue(): JsonValue = jsonMapOf(
            "timestamp" to dateMillis,
            "operation" to operation,
            "identifier" to identifier
        ).toJsonValue()
    }

    companion object {
        private const val OPERATIONS_KEY = "com.urbanairship.contacts.OPERATIONS"
        private const val OPERATION_ENTRIES_KEY = "com.urbanairship.contacts.OPERATION_ENTRIES"

        private const val ANON_CONTACT_DATA_KEY = "com.urbanairship.contacts.ANON_CONTACT_DATA_KEY"
        private const val LAST_CONTACT_IDENTITY_KEY = "com.urbanairship.contacts.LAST_CONTACT_IDENTITY_KEY"
        internal const val IDENTITY_RATE_LIMIT = "Contact.identify"
        internal const val UPDATE_RATE_LIMIT = "Contact.update"
    }
}
