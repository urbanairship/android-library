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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
        var mergedLiveUpdates = mutableListOf<LiveUpdateMutation>()

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
     * Owns the on-disk pending-updates list and serializes every access to it through a
     * single-consumer [Channel]. [Channel.trySend] is synchronous and atomic, so [add] /
     * [clear] claim their slot in caller-observed order regardless of dispatcher scheduling.
     * Suspending [read] / [hasPending] / [popFront] post a task with a [CompletableDeferred]
     * and await it, so they only resolve after every operation enqueued before them has run.
     * The one-time legacy migration is enqueued at construction so it always wins the queue.
     */
    internal class Storage(
        private val dataStore: PreferenceStore,
        scope: CoroutineScope,
    ) {
        private val dataQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

        init {
            dataQueue.trySend { migrateInternal() }
            scope.launch { for (op in dataQueue) op() }
        }

        /** Fire-and-forget append. */
        fun add(update: AudienceUpdate) {
            dataQueue.trySend {
                val list = readInternal().toMutableList()
                list.add(update)
                writeInternal(list)
            }
        }

        /** Fire-and-forget clear. */
        fun clear() {
            dataQueue.trySend { dataStore.remove(UPDATE_DATASTORE_KEY) }
        }

        suspend fun hasPending(): Boolean = enqueueRead {
            !(dataStore.get(UPDATE_DATASTORE_KEY)?.optList()?.isEmpty ?: true)
        }

        suspend fun read(): List<AudienceUpdate> = enqueueRead { readInternal() }

        /** Removes [uploaded] from the front of the stored list (in submission order). */
        suspend fun popFront(uploaded: List<AudienceUpdate>) {
            val done = CompletableDeferred<Unit>()
            dataQueue.trySend {
                val stored = readInternal().toMutableList()
                uploaded.forEach {
                    if (stored.firstOrNull() == it) {
                        stored.removeAt(0)
                    }
                }
                writeInternal(stored)
                done.complete(Unit)
            }
            done.await()
        }

        @VisibleForTesting
        internal suspend fun migrate() {
            val done = CompletableDeferred<Unit>()
            dataQueue.trySend {
                migrateInternal()
                done.complete(Unit)
            }
            done.await()
        }

        private suspend fun <T> enqueueRead(read: suspend () -> T): T {
            val deferred = CompletableDeferred<T>()
            dataQueue.trySend { deferred.complete(read()) }
            return deferred.await()
        }

        /** Must be invoked from inside [dataQueue]. */
        private suspend fun migrateInternal() {
            val attributes = dataStore.get(ATTRIBUTE_DATASTORE_KEY)?.list?.map {
                AttributeMutation.fromJsonList(it.optList())
            }?.flatten()

            val subscriptions = dataStore.get(SUBSCRIPTION_LISTS_DATASTORE_KEY)?.list?.map {
                SubscriptionListMutation.fromJsonList(it.optList())
            }?.flatten()

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

        /** Must be invoked from inside [dataQueue]. */
        private suspend fun readInternal(): List<AudienceUpdate> =
            dataStore.get(UPDATE_DATASTORE_KEY)?.tryParse(true) { json ->
                json.optList().map { AudienceUpdate(it.requireMap()) }
            } ?: emptyList()

        /** Must be invoked from inside [dataQueue]. */
        private suspend fun writeInternal(value: List<AudienceUpdate>) {
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
