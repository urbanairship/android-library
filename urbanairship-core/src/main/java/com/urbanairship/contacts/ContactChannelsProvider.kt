/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import com.urbanairship.AirshipDispatchers
import com.urbanairship.PrivacyManager
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.AutoRefreshingDataProvider
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

internal class ContactChannelsProvider(
    private val apiClient: ContactChannelsApiClient,
    private val privacyManager: PrivacyManager,
    stableContactIdUpdates: Flow<String>,
    overrideUpdates: Flow<AudienceOverrides.Contact>,
    clock: Clock = Clock.DEFAULT_CLOCK,
    taskSleeper: TaskSleeper = TaskSleeper.default,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AutoRefreshingDataProvider<List<ContactChannel>, AudienceOverrides.Contact> (
    identifierUpdates = stableContactIdUpdates,
    overrideUpdates = overrideUpdates,
    clock = clock,
    taskSleeper = taskSleeper,
    dispatcher = dispatcher
) {

    constructor(
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        stableContactIdUpdates: Flow<String>,
        overrideUpdates: Flow<AudienceOverrides.Contact>,
    ): this(
        apiClient = ContactChannelsApiClient(config),
        privacyManager = privacyManager,
        stableContactIdUpdates = stableContactIdUpdates,
        overrideUpdates = overrideUpdates
    )

    private val addressToChannelIdMap = mutableMapOf<String, String>()
    private val lock = ReentrantLock()

    override suspend fun onFetch(identifier: String): Result<List<ContactChannel>> {
        if (!privacyManager.isContactsEnabled) {
            return Result.failure(
                IllegalStateException("Unable to fetch subscriptions when FEATURE_TAGS_AND_ATTRIBUTES or FEATURE_CONTACTS are disabled")
            )
        }

        val result = apiClient.fetch(identifier)
        if (result.isSuccessful && result.value != null) {
            return Result.success(result.value)
        }

        return Result.failure(
            result.exception ?: IllegalStateException("Missing response body")
        )
    }

    override fun onApplyOverrides(data: List<ContactChannel>, overrides: AudienceOverrides.Contact): List<ContactChannel> {
        val channelMutations = overrides.channels
        if (channelMutations.isNullOrEmpty()) {
            return data
        }

        lock.withLock {
            /// Update map with any address to channel Id before trying to match
            channelMutations.forEach {
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

        val mutable = data.toMutableList()
        channelMutations.forEach { mutation ->
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
