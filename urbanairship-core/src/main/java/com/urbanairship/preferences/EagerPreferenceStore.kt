/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Eager preference store. Loads all non-lazy rows from the database at startup and keeps them
 * in an in-memory map; subsequent reads are synchronous, writes are scheduled on a serial
 * dispatcher (or block via [putSync]).
 *
 * Values are stored as raw strings — typed conversion is handled one level up in
 * [PreferenceStore] via [SyncPrefKey] / [AsyncPrefKey]. Reached only through that wrapper.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EagerPreferenceStore internal constructor(
    private val dao: PreferenceDao,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {

    private val scope = CoroutineScope(dispatcher)
    private val cacheLock = ReentrantLock()
    private val cache: MutableMap<String, String> = mutableMapOf()

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
                deleteAsync(key, e)
                continue
            }
            if (row?.value != null) {
                loaded += key to row.value
            } else {
                deleteAsync(key)
            }
        }
        applyToCache(loaded)
    }

    private fun deleteAsync(key: String, throwable: Throwable? = null) {
        UALog.e(throwable) { "Unable to fetch preference value. Deleting: $key" }
        scope.launch {
            runCatching { dao.delete(key) }.onFailure { UALog.e(it, "Failed to delete preference $key") }
        }
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
     * Updates the cache and schedules a DB write on the serial dispatcher. Passing `null` deletes
     * the row. Returns immediately; the actual disk write completes asynchronously.
     *
     * The cache update and the launch are scheduled atomically under [cacheLock] so the serial
     * dispatcher receives writes in the same order callers updated the cache.
     */
    public fun put(key: String, value: String?): Unit = cacheLock.withLock {
        if (setCacheLocked(key, value)) {
            scope.launch { writeValue(key, value) }
        }
    }

    /**
     * Blocking variant of [put] — waits for the database write to commit. Returns `true` on
     * success. Use only when correctness depends on the write being durable (e.g., a migration
     * that deletes the legacy key only if the new key was written).
     *
     * Writes are scheduled on the same serial dispatcher as [put], so a concurrent [put]
     * cannot reorder the DB writes relative to the cache updates.
     */
    public fun putSync(key: String, value: String?): Boolean = runBlocking {
        val job = cacheLock.withLock {
            setCacheLocked(key, value)
            scope.async { writeValue(key, value) }
        }
        job.await()
    }

    /** Removes [key]. Equivalent to `put(key, null)`. */
    public fun remove(key: String): Unit = put(key, null)

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

    private suspend fun writeValue(key: String, value: String?): Boolean = try {
        if (value == null) {
            UALog.v("Removing preference: %s", key)
            dao.delete(key)
        } else {
            UALog.v("Saving preference: %s value: %s", key, value)
            dao.upsert(PreferenceData(key = key, value = value, lazy = false))
        }
        true
    } catch (e: Exception) {
        UALog.e(e, "Failed to write preference %s:%s", key, value)
        false
    }

    internal companion object {

        /** Constructs an [EagerPreferenceStore] from [dao] and populates the in-memory cache. */
        internal suspend fun load(dao: PreferenceDao): EagerPreferenceStore =
            EagerPreferenceStore(dao).also { it.loadPreferences() }
    }
}
