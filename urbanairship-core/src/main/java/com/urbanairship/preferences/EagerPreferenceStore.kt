/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.db.guardDao
import com.urbanairship.util.AsyncSerialQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope

/**
 * Eager preference store. Loads all non-lazy rows from the database at startup and keeps them
 * in an in-memory map; subsequent reads are synchronous, writes are chained so they commit in
 * submission order.
 *
 * Values are stored as raw strings — typed conversion is handled one level up in
 * [PreferenceStore] via [SyncPrefKey] / [AsyncPrefKey]. Reached only through that wrapper.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EagerPreferenceStore internal constructor(
    private val dao: PreferenceDao,
    scope: CoroutineScope = CoroutineScope(AirshipDispatchers.newSerialDispatcher())
) {

    private val cacheLock = ReentrantLock()
    private val cache: MutableMap<String, String> = mutableMapOf()
    private val writeQueue = AsyncSerialQueue(scope)

    /**
     * Populates the in-memory cache from non-lazy database rows. Falls back to per-key load
     * if the batch query fails and drops corrupt rows along the way.
     */
    @VisibleForTesting
    internal suspend fun loadPreferences() {
        try {
            val rows = dao.queryEagerPreferences()
            applyToCache(rows.map { it.key to it.value })
        } catch (e: Exception) {
            UALog.e(e, "Failed to load preferences. Retrying with fallback loading.")
            fallbackLoad()
        }
    }

    private suspend fun fallbackLoad() {
        val keys = try {
            dao.queryEagerKeys()
        } catch (e: Exception) {
            UALog.e(e, "Failed to load keys.")
            null
        }

        if (keys.isNullOrEmpty()) {
            UALog.e("Unable to load keys, deleting preference store.")
            try {
                dao.deleteAll()
            } catch (e: Exception) {
                UALog.e(e, "Failed to delete preferences.")
            }
            return
        }

        val loaded = mutableListOf<Pair<String, String?>>()
        for (key in keys) {
            val row = try {
                dao.findRow(key)
            } catch (e: Throwable) {
                deleteCorruptKey(key, e)
                continue
            }
            if (row?.value != null) {
                loaded += key to row.value
            } else {
                deleteCorruptKey(key)
            }
        }
        applyToCache(loaded)
    }

    private suspend fun deleteCorruptKey(key: String, throwable: Throwable? = null) {
        UALog.e(throwable) { "Unable to fetch preference value. Deleting: $key" }
        guardDao("delete($key)", Unit) { dao.delete(key) }
    }

    private fun applyToCache(entries: List<Pair<String, String?>>) {
        cacheLock.withLock {
            entries.forEach { (k, v) ->
                if (v == null) cache.remove(k) else cache[k] = v
            }
        }
    }

    /** Returns `true` if the cache has a value for [key]. */
    public fun isSet(key: String): Boolean = cacheLock.withLock { cache.containsKey(key) }

    /** Returns the cached value for [key], or `null` if unset. */
    public fun get(key: String): String? = cacheLock.withLock { cache[key] }

    /**
     * Updates the cache and enqueues a DB write on [writeQueue]. Passing `null` deletes the
     * row. Returns immediately; the actual disk write completes asynchronously.
     *
     * The cache update and the [writeQueue] enqueue happen under [cacheLock] so two
     * concurrent callers can't see a stale cache snapshot or enqueue writes out of
     * caller-observed order.
     */
    public fun put(key: String, value: String?): Unit = cacheLock.withLock {
        if (setCacheLocked(key, value)) {
            writeQueue.enqueue { writeValue(key, value) }
        }
    }

    /** Removes [key]. Equivalent to `put(key, null)`. */
    public fun remove(key: String): Unit = put(key, null)

    /**
     * Queues a no-op behind every pending write and awaits it — returns once every write
     * enqueued before this call has committed. For tests that need to inspect DB state
     * after a [put].
     */
    @VisibleForTesting
    internal suspend fun awaitPendingWrites() {
        writeQueue.enqueueAndAwait { }
    }

    /**
     * Caller must hold [cacheLock]. Returns `true` if the cache changed.
     */
    private fun setCacheLocked(key: String, value: String?): Boolean {
        val prev = cache[key]
        val absent = !cache.containsKey(key)
        return when {
            value == null && absent -> false
            value != null && prev == value -> false
            value == null -> {
                cache.remove(key)
                UALog.v("Preference updated: %s", key)
                true
            }
            else -> {
                cache[key] = value
                UALog.v("Preference updated: %s", key)
                true
            }
        }
    }

    private suspend fun writeValue(key: String, value: String?): Boolean = guardDao("writeValue($key)", false) {
        if (value == null) {
            UALog.v("Removing preference: %s", key)
            dao.delete(key)
        } else {
            UALog.v("Saving preference: %s value: %s", key, value)
            dao.upsert(PreferenceData(key = key, value = value, lazy = false))
        }
        true
    }

    internal companion object {

        /** Constructs an [EagerPreferenceStore] from [dao] and populates the in-memory cache. */
        internal suspend fun load(dao: PreferenceDao): EagerPreferenceStore =
            EagerPreferenceStore(dao).also { it.loadPreferences() }
    }
}
