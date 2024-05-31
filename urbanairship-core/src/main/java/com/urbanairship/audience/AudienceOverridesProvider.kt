/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.contacts.ContactChannelMutation
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListMutation
import com.urbanairship.util.CachedList
import com.urbanairship.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

internal class AudienceOverridesProvider(clock: Clock = Clock.DEFAULT_CLOCK) {
    companion object {
        internal const val EXPIRY_MS: Long = 600000 // 10 minutes
    }

    var stableContactIdDelegate: (suspend () -> String)? = null
    var pendingChannelOverridesDelegate: ((String) -> AudienceOverrides.Channel)? = null
    var pendingContactOverridesDelegate: ((String) -> AudienceOverrides.Contact)? = null

    private val records = CachedList<Record<*>>(clock)

    private val _updates = MutableStateFlow(0U)
    val updates: SharedFlow<UInt> = _updates.asSharedFlow()

    fun notifyPendingChanged() {
       _updates.update { it.inc() }
    }

    fun recordContactUpdate(
        contactId: String,
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<ScopedSubscriptionListMutation>? = null,
        channel: ContactChannelMutation? = null
    ) {
        val overrides = AudienceOverrides.Contact(tags, attributes, subscriptions, channel?.let { listOf(it) })
        records.append(Record(contactId, overrides), EXPIRY_MS)
        notifyPendingChanged()
    }

    fun recordChannelUpdate(
        channelId: String,
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<SubscriptionListMutation>? = null
    ) {
        val overrides = AudienceOverrides.Channel(tags, attributes, subscriptions)
        records.append(Record(channelId, overrides), EXPIRY_MS)
        notifyPendingChanged()
    }

    private fun convertAppScopes(scoped: List<ScopedSubscriptionListMutation>): List<SubscriptionListMutation> {
        return scoped.mapNotNull { mutation ->
            if (mutation.scope == Scope.APP) {
                SubscriptionListMutation(mutation.action, mutation.listId, mutation.timestamp)
            } else {
                null
            }
        }
    }

    suspend fun contactOverrides(contactId: String?): AudienceOverrides.Contact {
        val resolvedContactId = contactId ?: stableContactIdDelegate?.invoke() ?: return AudienceOverrides.Contact()
        val pendingContact = pendingContactOverridesDelegate?.invoke(resolvedContactId)

        val tags = mutableListOf<TagGroupsMutation>()
        val attributes = mutableListOf<AttributeMutation>()
        val subscriptions = mutableListOf<ScopedSubscriptionListMutation>()
        val channels = mutableListOf<ContactChannelMutation>()

        // Apply only contact updates
        this.records.values.forEach { record ->
            if (record.overrides is AudienceOverrides.Contact && record.identifier == resolvedContactId) {
                record.overrides.tags?.let { tags += it }
                record.overrides.attributes?.let { attributes += it }
                record.overrides.subscriptions?.let { subscriptions += it }
                record.overrides.channels?.let { channels += it }
            }
        }

        // Pending contact
        pendingContact?.tags?.let { tags += it }
        pendingContact?.attributes?.let { attributes += it }
        pendingContact?.subscriptions?.let { subscriptions += it }
        pendingContact?.channels?.let { channels += it }

        return AudienceOverrides.Contact(
            tags.ifEmpty { null },
            attributes.ifEmpty { null },
            subscriptions.ifEmpty { null },
            channels.ifEmpty { null }
        )
    }

    suspend fun channelOverrides(channelId: String, contactId: String? = null): AudienceOverrides.Channel {
        val resolvedContactId = contactId ?: stableContactIdDelegate?.invoke()
        val pendingChannel = pendingChannelOverridesDelegate?.invoke(channelId)
        val pendingContact = resolvedContactId?.let { pendingContactOverridesDelegate?.invoke(it) }

        val tags = mutableListOf<TagGroupsMutation>()
        val attributes = mutableListOf<AttributeMutation>()
        val subscriptions = mutableListOf<SubscriptionListMutation>()

        // Apply both contact and channel updates first
        this.records.values.forEach { record ->
            when (record.overrides) {
                is AudienceOverrides.Channel -> {
                    if (record.identifier == channelId) {
                        record.overrides.tags?.let { tags += it }
                        record.overrides.attributes?.let { attributes += it }
                        record.overrides.subscriptions?.let { subscriptions += it }
                    }
                }
                is AudienceOverrides.Contact -> {
                    if (record.identifier == resolvedContactId) {
                        record.overrides.tags?.let { tags += it }
                        record.overrides.attributes?.let { attributes += it }
                        record.overrides.subscriptions?.let { subscriptions += convertAppScopes(it) }
                    }
                }
            }
        }

        // Pending channel
        pendingChannel?.tags?.let { tags += it }
        pendingChannel?.attributes?.let { attributes += it }
        pendingChannel?.subscriptions?.let { subscriptions += it }

        // Pending contact
        pendingContact?.tags?.let { tags += it }
        pendingContact?.attributes?.let { attributes += it }
        pendingContact?.subscriptions?.let { subscriptions += convertAppScopes(it) }

        return AudienceOverrides.Channel(
            tags.ifEmpty { null },
            attributes.ifEmpty { null },
            subscriptions.ifEmpty { null }
        )
    }

    private data class Record<T : AudienceOverrides>(
        val identifier: String,
        val overrides: T
    )
}
