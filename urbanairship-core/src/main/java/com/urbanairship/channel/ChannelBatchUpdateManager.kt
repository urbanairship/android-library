/* Copyright Airship and Contributors */

package com.urbanairship.channel

import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.preferences.AsyncPrefKey
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.tryParse
import com.urbanairship.util.AsyncSerialQueue
import com.urbanairship.util.AsyncSerialQueueScope
import kotlinx.coroutines.CoroutineScope

/** Handles updates for the batch endpoint. */
internal class ChannelBatchUpdateManager(
    private val apiClient: ChannelBatchUpdateApiClient,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    private val storage: Storage,
) {
    constructor(
        dataStore: PreferenceStore,
        apiClient: ChannelBatchUpdateApiClient,
        audienceOverridesProvider: AudienceOverridesProvider,
        scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO),
    ) : this(apiClient, audienceOverridesProvider, Storage(dataStore, scope))

    constructor(
        dataStore: PreferenceStore,
        runtimeConfig: AirshipRuntimeConfig,
        audienceOverridesProvider: AudienceOverridesProvider
    ) : this(dataStore, ChannelBatchUpdateApiClient(runtimeConfig), audienceOverridesProvider)

    init {
        audienceOverridesProvider.pendingChannelOverridesDelegate = {
            this.pendingOverrides()
        }
    }

    internal suspend fun hasPending(): Boolean = storage.hasPending()

    internal fun addUpdate(
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<SubscriptionListMutation>? = null,
        liveUpdates: List<LiveUpdateMutation>? = null,
    ) {
        if (tags.isNullOrEmpty() && attributes.isNullOrEmpty() && subscriptions.isNullOrEmpty() && liveUpdates.isNullOrEmpty()) {
            return
        }
        storage.add(
            AudienceUpdate(
                tags = tags,
                attributes = attributes,
                subscriptions = subscriptions,
                liveUpdates = liveUpdates
            )
        )
    }

    internal fun clearPending() {
        storage.clear()
    }

    internal suspend fun uploadPending(channelId: String): Boolean {
        val updates = storage.read()

        var mergedTags = mutableListOf<TagGroupsMutation>()
        var mergedAttributes = mutableListOf<AttributeMutation>()
        var mergedSubLists = mutableListOf<SubscriptionListMutation>()
        val mergedLiveUpdates = mutableListOf<LiveUpdateMutation>()

        updates.forEach { update ->
            update.tags?.let { mergedTags.addAll(it) }
            update.attributes?.let { mergedAttributes.addAll(it) }
            update.subscriptions?.let { mergedSubLists.addAll(it) }
            update.liveUpdates?.let { mergedLiveUpdates.addAll(it) }
        }

        mergedTags = TagGroupsMutation.collapseMutations(mergedTags).toMutableList()
        mergedAttributes = AttributeMutation.collapseMutations(mergedAttributes).toMutableList()
        mergedSubLists = SubscriptionListMutation.collapseMutations(mergedSubLists).toMutableList()

        if (mergedTags.isEmpty() && mergedAttributes.isEmpty() && mergedSubLists.isEmpty() && mergedLiveUpdates.isEmpty()) {
            storage.popFront(updates)
            return true
        }

        val response = apiClient.update(channelId, mergedTags, mergedAttributes, mergedSubLists, mergedLiveUpdates)

        if (response.isSuccessful || response.isClientError) {
            if (response.isSuccessful) {
                audienceOverridesProvider.recordChannelUpdate(
                    channelId,
                    mergedTags,
                    mergedAttributes,
                    mergedSubLists
                )
            }

            storage.popFront(updates)
            return true
        }

        return false
    }

    private suspend fun pendingOverrides(): AudienceOverrides.Channel {
        val updates = storage.read()
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
    internal suspend fun migrateData() {
        storage.migrate()
    }

    /**
     * Owns the on-disk pending-updates list and serializes every access to it through an
     * [AsyncSerialQueue]. The legacy migration is enqueued as the first link at construction
     * so any later read or write queues behind it.
     */
    internal class Storage(
        private val dataStore: PreferenceStore,
        scope: CoroutineScope,
    ) {
        private val queue = AsyncSerialQueue(scope)

        init {
            queue.enqueue { migrateInternal() }
        }

        /** Fire-and-forget append. */
        fun add(update: AudienceUpdate) {
            queue.enqueue {
                val list = readInternal().toMutableList()
                list.add(update)
                writeInternal(list)
            }
        }

        /** Fire-and-forget clear. */
        fun clear() {
            queue.enqueue { dataStore.remove(UPDATE_DATASTORE_KEY) }
        }

        suspend fun hasPending(): Boolean = queue.enqueueAndAwait {
            !(dataStore.get(UPDATE_DATASTORE_KEY)?.optList()?.isEmpty ?: true)
        }

        suspend fun read(): List<AudienceUpdate> = queue.enqueueAndAwait { readInternal() }

        /** Removes [uploaded] from the front of the stored list (in submission order). */
        suspend fun popFront(uploaded: List<AudienceUpdate>) {
            queue.enqueueAndAwait {
                val stored = readInternal().toMutableList()
                uploaded.forEach {
                    if (stored.firstOrNull() == it) {
                        stored.removeAt(0)
                    }
                }
                writeInternal(stored)
            }
        }

        @VisibleForTesting
        internal suspend fun migrate() {
            queue.enqueueAndAwait { migrateInternal() }
        }

        private suspend fun AsyncSerialQueueScope.migrateInternal() {
            val attributes = dataStore.get(ATTRIBUTE_DATASTORE_KEY)?.list?.flatMap {
                AttributeMutation.fromJsonList(it.optList())
            }

            val subscriptions = dataStore.get(SUBSCRIPTION_LISTS_DATASTORE_KEY)?.list?.flatMap {
                SubscriptionListMutation.fromJsonList(it.optList())
            }

            val tags = dataStore.get(TAG_GROUP_DATASTORE_KEY)?.list?.map {
                TagGroupsMutation.fromJsonValue(it)
            }

            if (!tags.isNullOrEmpty() || !attributes.isNullOrEmpty() || !subscriptions.isNullOrEmpty()) {
                val merged = readInternal() + AudienceUpdate(tags, attributes, subscriptions)
                writeInternal(merged)
            }

            dataStore.remove(ATTRIBUTE_DATASTORE_KEY)
            dataStore.remove(SUBSCRIPTION_LISTS_DATASTORE_KEY)
            dataStore.remove(TAG_GROUP_DATASTORE_KEY)
        }

        private suspend fun AsyncSerialQueueScope.readInternal(): List<AudienceUpdate> =
            dataStore.get(UPDATE_DATASTORE_KEY)?.tryParse(true) { json ->
                json.optList().map { AudienceUpdate(it.requireMap()) }
            } ?: emptyList()

        private suspend fun AsyncSerialQueueScope.writeInternal(value: List<AudienceUpdate>) {
            dataStore.put(UPDATE_DATASTORE_KEY, JsonValue.wrap(value))
        }

        internal companion object {
            // Legacy migration keys
            private val ATTRIBUTE_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.push.ATTRIBUTE_DATA_STORE")
            private val TAG_GROUP_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.push.PENDING_TAG_GROUP_MUTATIONS")
            private val SUBSCRIPTION_LISTS_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.push.PENDING_SUBSCRIPTION_MUTATIONS")

            // Updates storage key
            private val UPDATE_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.channel.PENDING_AUDIENCE_UPDATES")
        }
    }
}

internal data class AudienceUpdate(
    val tags: List<TagGroupsMutation>? = null,
    val attributes: List<AttributeMutation>? = null,
    val subscriptions: List<SubscriptionListMutation>? = null,
    val liveUpdates: List<LiveUpdateMutation>? = null,
) : JsonSerializable {

    constructor(json: JsonMap) : this(
        tags = json[TAGS]?.optList()?.let { list ->
            list.map { TagGroupsMutation.fromJsonValue(it) }
        },
        attributes = json[ATTRIBUTES]?.optList()?.let { list ->
            list.map { AttributeMutation.fromJsonValue(it) }
        },
        subscriptions = json[SUBSCRIPTION_LISTS]?.optList()?.let { list ->
            list.map { SubscriptionListMutation.fromJsonValue(it) }
        },
        liveUpdates = json[LIVE_UPDATES]?.optList()?.let { list ->
            list.map { LiveUpdateMutation.fromJson(it.requireMap()) }
        }
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        TAGS to tags,
        ATTRIBUTES to attributes,
        SUBSCRIPTION_LISTS to subscriptions,
        LIVE_UPDATES to liveUpdates
    ).toJsonValue()

    private companion object {
        private const val TAGS = "tags"
        private const val ATTRIBUTES = "attributes"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
        private const val LIVE_UPDATES = "live_updates"
    }
}
