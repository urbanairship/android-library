/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import com.urbanairship.AirshipDispatchers
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
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
import kotlinx.coroutines.delay
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
    private val cachedResponse  = CachedValue<Pair<String, List<ContactChannel>>>(clock)

    /// Map to cache address to channels to make matching easier
    private val addressToChannelIdMap = mutableMapOf<String, String>()
    private val lock = ReentrantLock()


    internal constructor(
        config: AirshipRuntimeConfig,
        audienceOverridesProvider: AudienceOverridesProvider,
        contactUpdates: StateFlow<ContactIdUpdate?>
    ): this(
        apiClient = ContactChannelsApiClient(config),
        audienceOverridesProvider = audienceOverridesProvider,
        contactUpdates = contactUpdates
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactChannels: SharedFlow<Result<List<ContactChannel>>> = contactUpdates.mapNotNull {
        if (it?.isStable == true) { it.contactId } else { null }
    }.flatMapLatest { contactId ->
        val fetchUpdates = flow {

            var backoff: Duration = initialBackoff
            var isFirstFetch = true

            while (true) {
                val fetched = fetch(contactId)
                backoff = if (fetched.isSuccess) {
                    emit(fetched)
                    taskSleeper.sleep(cachedResponse.remainingCacheTimeMillis().milliseconds)
                    initialBackoff
                } else {
                    if (isFirstFetch) {
                        emit(fetched)
                    }
                    taskSleeper.sleep(backoff)
                    backoff.times(2).coerceAtMost(maxBackoff)
                }
                isFirstFetch = false
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

    private suspend fun fetch(contactId: String): Result<List<ContactChannel>> {
        val cached = cachedResponse.get()
        if (cached != null && cached.first == contactId) {
            return Result.success(cached.second)
        }

        val result = apiClient.fetch(contactId)
        if (result.isSuccessful && result.value != null) {
            cachedResponse.set(
                Pair(contactId, result.value),
                clock.currentTimeMillis() + maxCacheAge.inWholeMilliseconds
            )
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
