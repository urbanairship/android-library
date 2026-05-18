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

/**
 * Handles updates for the batch endpoint.
 *
 * All preference reads/writes go through [opQueue], an unbuffered FIFO channel drained by a
 * single consumer coroutine. [Channel.trySend] is synchronous and atomic, so [addUpdate] /
 * [clearPending] claim their slot in caller-observed order without depending on dispatcher
 * scheduling. Suspending reads ([hasPending], [uploadPending], [pendingOverrides]) post a
 * task with a [CompletableDeferred] and await it, so they only resolve after every operation
 * enqueued before them has run. The one-time legacy migration is enqueued at construction so
 * it always wins the queue.
 */
internal class ChannelBatchUpdateManager(
    private val dataStore: PreferenceStore,
    private val apiClient: ChannelBatchUpdateApiClient,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO),
) {
    constructor(
        dataStore: PreferenceStore,
        runtimeConfig: AirshipRuntimeConfig,
        audienceOverridesProvider: AudienceOverridesProvider
    ) : this(
        dataStore,
        ChannelBatchUpdateApiClient(runtimeConfig),
        audienceOverridesProvider
    )

    private val opQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        // Migration is the first item on the queue; everything else queues behind it.
        opQueue.trySend { migrateInternal() }

        scope.launch {
            for (op in opQueue) op()
        }

        audienceOverridesProvider.pendingChannelOverridesDelegate = {
            this.pendingOverrides()
        }
    }

    internal suspend fun hasPending(): Boolean = enqueueRead {
        !(dataStore.get(UPDATE_DATASTORE_KEY)?.optList()?.isEmpty ?: true)
    }

    /** Fire-and-forget: synchronously claims its slot on the op queue. */
    internal fun addUpdate(
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<SubscriptionListMutation>? = null,
        liveUpdates: List<LiveUpdateMutation>? = null,
    ) {
        if (tags.isNullOrEmpty() && attributes.isNullOrEmpty() && subscriptions.isNullOrEmpty() && liveUpdates.isNullOrEmpty()) {
            return
        }

        val update = AudienceUpdate(
            tags = tags,
            attributes = attributes,
            subscriptions = subscriptions,
            liveUpdates = liveUpdates
        )

        opQueue.trySend {
            val list = readUpdates().toMutableList()
            list.add(update)
            writeUpdates(list)
        }
    }

    /** Fire-and-forget: synchronously claims its slot on the op queue. */
    internal fun clearPending() {
        opQueue.trySend { dataStore.remove(UPDATE_DATASTORE_KEY) }
    }

    internal suspend fun uploadPending(channelId: String): Boolean {
        val updates = enqueueRead { readUpdates() }

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
            popAudienceUpdates(updates)
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

            popAudienceUpdates(updates)
            return true
        }

        return false
    }

    private suspend fun popAudienceUpdates(uploaded: List<AudienceUpdate>) {
        val done = CompletableDeferred<Unit>()
        opQueue.trySend {
            val stored = readUpdates().toMutableList()
            uploaded.forEach {
                if (stored.firstOrNull() == it) {
                    stored.removeAt(0)
                }
            }
            writeUpdates(stored)
            done.complete(Unit)
        }
        done.await()
    }

    private suspend fun pendingOverrides(): AudienceOverrides.Channel {
        val updates = enqueueRead { readUpdates() }
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

    /**
     * Posts [read] on the op queue and awaits its result — used by the suspending public API
     * to share the queue with fire-and-forget writes.
     */
    private suspend fun <T> enqueueRead(read: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()
        opQueue.trySend {
            deferred.complete(read())
        }
        return deferred.await()
    }

    @VisibleForTesting
    internal suspend fun migrateData() {
        val done = CompletableDeferred<Unit>()
        opQueue.trySend {
            migrateInternal()
            done.complete(Unit)
        }
        done.await()
    }

    /** Must be invoked from inside [opQueue]. */
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
            val merged = readUpdates() + AudienceUpdate(tags, attributes, subscriptions)
            writeUpdates(merged)
        }

        dataStore.remove(ATTRIBUTE_DATASTORE_KEY)
        dataStore.remove(SUBSCRIPTION_LISTS_DATASTORE_KEY)
        dataStore.remove(TAG_GROUP_DATASTORE_KEY)
    }

    /** Must be invoked from inside [opQueue]. */
    private suspend fun readUpdates(): List<AudienceUpdate> =
        dataStore.get(UPDATE_DATASTORE_KEY)?.tryParse(true) { json ->
            json.optList().map { AudienceUpdate(it.requireMap()) }
        } ?: emptyList()

    /** Must be invoked from inside [opQueue]. */
    private suspend fun writeUpdates(value: List<AudienceUpdate>) {
        dataStore.put(UPDATE_DATASTORE_KEY, JsonValue.wrap(value))
    }

    internal companion object {
        // Migration keys
        private val ATTRIBUTE_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.push.ATTRIBUTE_DATA_STORE")
        private val TAG_GROUP_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.push.PENDING_TAG_GROUP_MUTATIONS")
        private val SUBSCRIPTION_LISTS_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.push.PENDING_SUBSCRIPTION_MUTATIONS")

        // Updates storage key
        private val UPDATE_DATASTORE_KEY = AsyncPrefKey.json("com.urbanairship.channel.PENDING_AUDIENCE_UPDATES")
    }
}

private data class AudienceUpdate(
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
