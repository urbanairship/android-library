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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
     * Owns the on-disk pending-updates list and serializes every access to it through a job
     * chain: each operation launches a coroutine that joins the previous op's [Job] before
     * running, so writes commit in caller-observed order. The "claim my predecessor and
     * publish my Job as the new tail" sequence runs under [chainLock] so two concurrent
     * callers can't both see the same predecessor — the launched jobs form a strict linear
     * chain. The legacy migration is enqueued as the first link at construction.
     */
    internal class Storage(
        private val dataStore: PreferenceStore,
        private val scope: CoroutineScope,
    ) {
        private val chainLock = ReentrantLock()

        /** Tail of the pending-op chain. Read/written only under [chainLock]. */
        private var lastOp: Job? = null

        init {
            enqueueOperation { migrateInternal() }
        }

        /** Fire-and-forget append. */
        fun add(update: AudienceUpdate) {
            enqueueOperation {
                val list = readInternal().toMutableList()
                list.add(update)
                writeInternal(list)
            }
        }

        /** Fire-and-forget clear. */
        fun clear() {
            enqueueOperation { dataStore.remove(UPDATE_DATASTORE_KEY) }
        }

        suspend fun hasPending(): Boolean = awaitOperation {
            !(dataStore.get(UPDATE_DATASTORE_KEY)?.optList()?.isEmpty ?: true)
        }

        suspend fun read(): List<AudienceUpdate> = awaitOperation { readInternal() }

        /** Removes [uploaded] from the front of the stored list (in submission order). */
        suspend fun popFront(uploaded: List<AudienceUpdate>) {
            awaitOperation {
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
            awaitOperation { migrateInternal() }
        }

        /**
         * Appends [block] to the chain. Returns immediately; [block] runs after the prior
         * tail completes. The very first call (from `init`) sees [lastOp] as `null`, so the
         * `?.join()` is a no-op — that call enqueues the migration as the first link.
         */
        private fun enqueueOperation(block: suspend () -> Unit) {
            chainLock.withLock {
                val previous = lastOp
                lastOp = scope.launch {
                    previous?.join()
                    block()
                }
            }
        }

        /**
         * Awaitable variant of [enqueueOperation] using [scope].async — if the scope is
         * canceled, the underlying Job is canceled and `await()` throws cleanly instead of
         * hanging on a never-completed `CompletableDeferred`.
         */
        private suspend fun <T> awaitOperation(block: suspend () -> T): T {
            val deferred = chainLock.withLock {
                val previous = lastOp
                scope.async {
                    previous?.join()
                    block()
                }.also { lastOp = it }
            }
            return deferred.await()
        }

        /** Must be invoked from within an [enqueueOperation] block. */
        private suspend fun migrateInternal() {
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

        /** Must be invoked from within an [enqueueOperation] block. */
        private suspend fun readInternal(): List<AudienceUpdate> =
            dataStore.get(UPDATE_DATASTORE_KEY)?.tryParse(true) { json ->
                json.optList().map { AudienceUpdate(it.requireMap()) }
            } ?: emptyList()

        /** Must be invoked from within an [enqueueOperation] block. */
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
