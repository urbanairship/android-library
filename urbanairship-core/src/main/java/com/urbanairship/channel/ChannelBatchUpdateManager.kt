/* Copyright Airship and Contributors */

package com.urbanairship.channel

import com.google.android.gms.common.util.VisibleForTesting
import com.urbanairship.PreferenceDataStore
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.tryParse
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Handles updates for the batch endpoint **/
internal class ChannelBatchUpdateManager(
    private val dataStore: PreferenceDataStore,
    private val apiClient: ChannelBatchUpdateApiClient,
    private val audienceOverridesProvider: AudienceOverridesProvider,
) {
    constructor(
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        audienceOverridesProvider: AudienceOverridesProvider
    ) : this(
        dataStore,
        ChannelBatchUpdateApiClient(runtimeConfig),
        audienceOverridesProvider
    )

    private val lock = ReentrantLock()

    internal val hasPending: Boolean
    get() {
        return !dataStore.getJsonValue(UPDATE_DATASTORE_KEY).optList().isEmpty
    }

    private var updates: List<AudienceUpdate>
        get() {
            return dataStore.optJsonValue(UPDATE_DATASTORE_KEY)?.tryParse { json ->
                json.requireList().map { AudienceUpdate(it.requireMap()) }
            } ?: emptyList()
        }
        set(value) {
            dataStore.put(UPDATE_DATASTORE_KEY, JsonValue.wrap(value))
        }

    init {
        migrateData()

        audienceOverridesProvider.pendingChannelOverridesDelegate = {
            this.pendingOverrides()
        }
    }

    internal fun clearPending() {
        lock.withLock {
            dataStore.remove(UPDATE_DATASTORE_KEY)
        }
    }

    internal suspend fun uploadPending(channelId: String): Boolean {
        val updates = this.updates

        var mergedTags = mutableListOf<TagGroupsMutation>()
        var mergedAttributes = mutableListOf<AttributeMutation>()
        var mergedSubLists = mutableListOf<SubscriptionListMutation>()

        updates.forEach { update ->
            update.tags?.let { mergedTags.addAll(it) }
            update.attributes?.let { mergedAttributes.addAll(it) }
            update.subscriptions?.let { mergedSubLists.addAll(it) }
        }

        mergedTags = TagGroupsMutation.collapseMutations(mergedTags)
        mergedAttributes = AttributeMutation.collapseMutations(mergedAttributes)
        mergedSubLists = SubscriptionListMutation.collapseMutations(mergedSubLists)

        if (mergedTags.isNullOrEmpty() && mergedAttributes.isNullOrEmpty() && mergedSubLists.isNullOrEmpty()) {
            popAudienceUpdates(updates)
            return true
        }

        val response = apiClient.update(channelId, mergedTags, mergedAttributes, mergedSubLists)
        if (response.isSuccessful || response.isClientError) {

            if (response.isSuccessful) {
                audienceOverridesProvider.recordChannelUpdate(
                    channelId,
                    mergedTags,
                    mergedAttributes,
                    mergedSubLists
                )
            }

            popAudienceUpdates(updates)
            return true
        }

        return false
    }

    internal fun addUpdate(
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<SubscriptionListMutation>? = null
    ) {
        if (tags.isNullOrEmpty() && attributes.isNullOrEmpty() && subscriptions.isNullOrEmpty()) {
            return
        }

        val update = AudienceUpdate(
            tags = tags,
            attributes = attributes,
            subscriptions = subscriptions
        )

        lock.withLock {
            val list = updates.toMutableList()
            list.add(update)
            updates = list
        }
    }

    private fun popAudienceUpdates(updates: List<AudienceUpdate>) {
        lock.withLock {
            val stored = updates.toMutableList()
            updates.forEach {
                if (stored[0] == it) {
                    stored.removeAt(0)
                }
            }
            this.updates = stored
        }
    }

    private fun pendingOverrides(): AudienceOverrides.Channel {
        val mergedTags = mutableListOf<TagGroupsMutation>()
        val mergedAttributes = mutableListOf<AttributeMutation>()
        val mergedSubLists = mutableListOf<SubscriptionListMutation>()
        updates.forEach { update ->
            update.tags?.let { mergedTags.addAll(it) }
            update.attributes?.let { mergedAttributes.addAll(it) }
            update.subscriptions?.let { mergedSubLists.addAll(it) }
        }
        return AudienceOverrides.Channel(
            mergedTags.ifEmpty { null },
            mergedAttributes.ifEmpty { null },
            mergedSubLists.ifEmpty { null }
        )
    }

    @VisibleForTesting
    internal fun migrateData() {
        // List of Lists
        val attributes = dataStore.getJsonValue(ATTRIBUTE_DATASTORE_KEY).list?.map {
            AttributeMutation.fromJsonList(it.optList())
        }?.flatten()

        // List of Lists
        val subscriptions = dataStore.getJsonValue(SUBSCRIPTION_LISTS_DATASTORE_KEY).list?.map {
            SubscriptionListMutation.fromJsonList(it.optList())
        }?.flatten()

        // Just a list
        val tags = dataStore.getJsonValue(TAG_GROUP_DATASTORE_KEY).list?.map {
            TagGroupsMutation.fromJsonValue(it)
        }

        addUpdate(tags, attributes, subscriptions)

        dataStore.remove(ATTRIBUTE_DATASTORE_KEY)
        dataStore.remove(SUBSCRIPTION_LISTS_DATASTORE_KEY)
        dataStore.remove(TAG_GROUP_DATASTORE_KEY)
    }

    internal companion object {
        // Migration keys
        private const val ATTRIBUTE_DATASTORE_KEY = "com.urbanairship.push.ATTRIBUTE_DATA_STORE"
        private const val TAG_GROUP_DATASTORE_KEY = "com.urbanairship.push.PENDING_TAG_GROUP_MUTATIONS"
        private const val SUBSCRIPTION_LISTS_DATASTORE_KEY = "com.urbanairship.push.PENDING_SUBSCRIPTION_MUTATIONS"

        // Updates storage key
        private const val UPDATE_DATASTORE_KEY = "com.urbanairship.channel.PENDING_AUDIENCE_UPDATES"
    }
}

private data class AudienceUpdate(
    val tags: List<TagGroupsMutation>? = null,
    val attributes: List<AttributeMutation>? = null,
    val subscriptions: List<SubscriptionListMutation>? = null,
) : JsonSerializable {

    constructor(json: JsonMap) : this(
        tags = json.get(TAGS)?.optList()?.let { list ->
            list.map { TagGroupsMutation.fromJsonValue(it) }
        },
        attributes = json.get(ATTRIBUTES)?.optList()?.let { list ->
            list.map { AttributeMutation.fromJsonValue(it) }
        },
        subscriptions = json.get(SUBSCRIPTION_LISTS)?.optList()?.let { list ->
            list.map { SubscriptionListMutation.fromJsonValue(it) }
        }
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        TAGS to tags,
        ATTRIBUTES to attributes,
        SUBSCRIPTION_LISTS to subscriptions,
    ).toJsonValue()

    private companion object {
        private const val TAGS = "tags"
        private const val ATTRIBUTES = "attributes"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
    }
}
