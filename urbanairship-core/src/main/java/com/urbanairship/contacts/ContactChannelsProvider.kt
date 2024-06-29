/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import com.urbanairship.AirshipDispatchers
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

internal class ContactChannelsProvider(
    private val apiClient: ContactChannelsApiClient,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    private val contactUpdates: StateFlow<ContactIdUpdate?>,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val taskSleeper: TaskSleeper = TaskSleeper.default,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    /// Map to cache address to channels to make matching easier
    private val addressToChannelIdMap = mutableMapOf<String, String>()
    private val lock = ReentrantLock()
    private val changeTokenFlow = MutableStateFlow(UUID.randomUUID())
    private val fetchCache = FetchCache(clock, maxCacheAge)
    internal constructor(
        config: AirshipRuntimeConfig,
        audienceOverridesProvider: AudienceOverridesProvider,
        contactUpdates: StateFlow<ContactIdUpdate?>
    ): this(
        apiClient = ContactChannelsApiClient(config),
        audienceOverridesProvider = audienceOverridesProvider,
        contactUpdates = contactUpdates
    )

    fun refresh() {
        changeTokenFlow.value = UUID.randomUUID()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactChannels: SharedFlow<Result<List<ContactChannel>>> by lazy {
        var lastContactId: String? = null

        val stableContactIdUpdates = contactUpdates.mapNotNull {
            if (it?.isStable == true) { it.contactId } else { null }
        }

        combine(stableContactIdUpdates, changeTokenFlow) { contactId, changeToken ->
            Pair(contactId, changeToken)
        }.flatMapLatest { (contactId, changeToken) ->
            val fetchUpdates = flow {

                var backoff: Duration = initialBackoff

                while (true) {
                    val fetched = fetch(contactId, changeToken)
                    backoff = if (fetched.isSuccess) {
                        emit(fetched)
                        taskSleeper.sleep(fetchCache.remainingCacheTimeMillis)
                        initialBackoff
                    } else {
                        if (lastContactId != contactId) {
                            emit(fetched)
                        }
                        taskSleeper.sleep(backoff)
                        backoff.times(2).coerceAtMost(maxBackoff)
                    }
                    lastContactId = contactId
                }
            }

            val overridesUpdates = audienceOverridesProvider.updates.map { _ ->
                audienceOverridesProvider.contactOverrides(contactId)
            }

            combine(fetchUpdates, overridesUpdates) { fetchUpdate, overrides ->
                fetchUpdate.fold(onSuccess = {
                    Result.success(applyOverrides(it, overrides.channels))
                }, onFailure = {
                    Result.failure(it)
                })
            }
        }.shareIn(
            scope  = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 100),
            replay =  1
        )
    }

    private suspend fun fetch(contactId: String, changeToken: UUID): Result<List<ContactChannel>> {
        val cached = fetchCache.getCache(contactId, changeToken)
        if (cached != null) {
            return Result.success(cached)
        }

        val result = apiClient.fetch(contactId)
        if (result.isSuccessful && result.value != null) {
            fetchCache.setCache(contactId, changeToken, result.value)
            return Result.success(result.value)
        }

        return Result.failure(
            result.exception ?: IllegalStateException("Missing response body")
        )
    }

    private fun applyOverrides(list: List<ContactChannel>, overrides: List<ContactChannelMutation>?): List<ContactChannel> {
        if (overrides.isNullOrEmpty()) {
            return list
        }

        lock.withLock {
            /// Update map with any address to channel Id before trying to match
            overrides.forEach {
                when (it) {
                    is ContactChannelMutation.Associate -> {
                        val address = it.channel.canonicalAddress
                        val channelId = it.channelId
                        if (address != null && channelId != null) {
                            addressToChannelIdMap[address] = channelId
                        }
                    }
                    is ContactChannelMutation.Disassociated -> {
                        val address = it.channel.canonicalAddress
                        val channelId = it.channelId
                        if (address != null && channelId != null) {
                            addressToChannelIdMap[address] = channelId
                        }
                    }
                    is ContactChannelMutation.AssociateAnon -> {
                        // No-op
                    }
                }
            }
        }

        val mutable = list.toMutableList()
        overrides.forEach { mutation ->
            when (mutation) {
                is ContactChannelMutation.Associate -> {
                    val found = mutable.firstOrNull {
                        isMatch(it, mutation)
                    }

                    if (found == null) {
                        mutable.add(mutation.channel)
                    }
                }
                is ContactChannelMutation.Disassociated -> {
                    mutable.removeAll {
                        isMatch(it, mutation)
                    }
                }
                is ContactChannelMutation.AssociateAnon -> {
                    // No-op
                }
            }
        }

        return mutable
    }

    private fun isMatch(
        channel: ContactChannel,
        mutation: ContactChannelMutation,
    ): Boolean {
        val canonicalAddress = channel.canonicalAddress
        val resolvedChannelId = resolveChannelId(channel.channelId, canonicalAddress)

        val mutationCanonicalAddress = mutation.canonicalAddress
        val mutationChannelId = resolveChannelId(mutation.channelId, mutationCanonicalAddress)

        if (resolvedChannelId != null && resolvedChannelId == mutationChannelId) {
            return true
        }

        return canonicalAddress != null && canonicalAddress == mutationCanonicalAddress
    }

    private fun resolveChannelId(channelId: String?, canonicalAddress: String?): String? {
        if (channelId != null) { return channelId }

        if (canonicalAddress != null) {
            return lock.withLock {
                addressToChannelIdMap[canonicalAddress]
            }
        }

        return null
    }

    companion object {
        private val maxCacheAge = 10.minutes
        private val initialBackoff = 8.seconds
        private val maxBackoff = 64.seconds
    }
}


private val ContactChannelMutation.canonicalAddress: String?
    get() {
        return when (this) {
            is ContactChannelMutation.Disassociated -> channel.canonicalAddress
            is ContactChannelMutation.Associate -> channel.canonicalAddress
            is ContactChannelMutation.AssociateAnon -> null
        }
    }

private val ContactChannelMutation.channelId: String?
    get() {
        return when (this) {
            is ContactChannelMutation.Disassociated -> this.channelId ?: this.channel.channelId
            is ContactChannelMutation.Associate -> this.channelId ?: this.channel.channelId
            is ContactChannelMutation.AssociateAnon -> this.channelId
        }
    }

private val ContactChannel.canonicalAddress: String?
    get() {
        return when (this) {
            is ContactChannel.Sms -> {
                when (this.registrationInfo) {
                    is ContactChannel.Sms.RegistrationInfo.Pending -> {
                        "${this.registrationInfo.address}:${this.senderId}"
                    }
                    else -> null
                }
            }
            is ContactChannel.Email -> {
                when (this.registrationInfo) {
                    is ContactChannel.Email.RegistrationInfo.Pending -> {
                        this.registrationInfo.address
                    }
                    else -> null
                }
            }
        }
    }

private val ContactChannel.channelId: String?
    get() {
        return when (this) {
            is ContactChannel.Sms -> {
                when (this.registrationInfo) {
                    is ContactChannel.Sms.RegistrationInfo.Registered -> {
                        this.registrationInfo.channelId
                    }
                    else -> null
                }
            }
            is ContactChannel.Email -> {
                when (this.registrationInfo) {
                    is ContactChannel.Email.RegistrationInfo.Registered -> {
                        this.registrationInfo.channelId
                    }
                    else -> null
                }
            }
        }
    }


private class FetchCache(private val clock: Clock, private val maxCacheAge: Duration) {
    private val cachedResponse  = CachedValue<Triple<String, UUID, List<ContactChannel>>>(clock)

    fun getCache(contactId: String, changeToken: UUID): List<ContactChannel>? {
        val cached = cachedResponse.get()
        if (cached != null && cached.first == contactId && cached.second == changeToken) {
            return cached.third
        }

        return null
    }

    fun setCache(contactId: String, changeToken: UUID, value: List<ContactChannel>) {
        cachedResponse.set(
            Triple(contactId, changeToken, value),
            clock.currentTimeMillis() + maxCacheAge.inWholeMilliseconds
        )
    }

    val remainingCacheTimeMillis: Duration
        get() = cachedResponse.remainingCacheTimeMillis().milliseconds

}
