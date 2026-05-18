/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog

/**
 * Container for Airship preference accessors.
 *
 * Typed access via [SyncPrefKey] / [AsyncPrefKey] is the preferred API. Sync keys resolve to
 * regular calls backed by an eagerly-loaded in-memory map; async keys resolve to `suspend` calls
 * backed by an on-demand database read. Each key carries its own serialize/deserialize logic, so
 * the store itself just stores and reads strings.
 *
 * Serialization errors are handled here, not in the keys: a `serialize` that throws on [put] is
 * logged and the write is dropped; a `deserialize` that throws on [get] is logged and the key is
 * treated as unset.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    private val syncStore: PreferenceDataStore,
    private val asyncStore: AsyncPreferenceStore = AsyncPreferenceStore(syncStore.dao)
) {

    /** Test-only window into the DAO for verifying lazy-column state. */
    @VisibleForTesting
    internal val dao: com.urbanairship.PreferenceDataDao get() = syncStore.dao

    // region Sync typed access

    /**
     * Returns the value stored under [key], or `null` if the key is unset, the stored value
     * cannot be deserialized, or deserialization throws (errors are logged).
     */
    public fun <T> get(key: SyncPrefKey<T>): T? {
        val stored = syncStore.getString(key.name, null) ?: return null
        return try {
            key.deserialize(stored)
        } catch (e: Throwable) {
            UALog.e(e) { "Failed to deserialize preference ${key.name}" }
            null
        }
    }

    /**
     * Stores [value] under [key]. Passing `null` removes the key. If [key]'s serializer throws,
     * the error is logged and the write is dropped.
     */
    public fun <T> put(key: SyncPrefKey<T>, value: T?) {
        if (value == null) {
            syncStore.remove(key.name)
            return
        }
        val serialized = trySerialize(key, value) ?: return
        syncStore.put(key.name, serialized)
    }

    /**
     * Blocking variant of [put] that waits for the underlying database write to commit and
     * returns `true` on success. Use this only when subsequent logic depends on the write being
     * durable — e.g., a migration that deletes the legacy key only if the new key was written.
     * Passing `null` removes the key (also blocking).
     */
    public fun <T> putSync(key: SyncPrefKey<T>, value: T?): Boolean {
        if (value == null) return syncStore.putSync(key.name, null)
        val serialized = trySerialize(key, value) ?: return false
        return syncStore.putSync(key.name, serialized)
    }

    /** Removes [key]. */
    public fun remove(key: SyncPrefKey<*>) {
        syncStore.remove(key.name)
    }

    /** Returns `true` if [key] has any stored value (even one that fails to deserialize). */
    public fun isSet(key: SyncPrefKey<*>): Boolean = syncStore.isSet(key.name)

    // endregion

    // region Async typed access (suspend)

    /**
     * Returns the value stored under [key], or `null` if the key is unset, the stored value
     * cannot be deserialized, or deserialization throws (errors are logged). The row is
     * self-migrated to `lazy = true` on first access so future takeoffs skip it.
     */
    public suspend fun <T> get(key: AsyncPrefKey<T>): T? {
        val stored = asyncStore.getString(key.name) ?: return null
        return try {
            key.deserialize(stored)
        } catch (e: Throwable) {
            UALog.e(e) { "Failed to deserialize preference ${key.name}" }
            null
        }
    }

    /**
     * Stores [value] under [key] with `lazy = true`. Passing `null` removes the key. If [key]'s
     * serializer throws, the error is logged and the write is dropped.
     */
    public suspend fun <T> put(key: AsyncPrefKey<T>, value: T?) {
        if (value == null) {
            asyncStore.remove(key.name)
            return
        }
        val serialized = trySerialize(key, value) ?: return
        asyncStore.put(key.name, serialized)
    }

    /** Removes [key]. */
    public suspend fun remove(key: AsyncPrefKey<*>) {
        asyncStore.remove(key.name)
    }

    /** Returns `true` if [key] has any stored value (even one that fails to deserialize). */
    public suspend fun isSet(key: AsyncPrefKey<*>): Boolean = asyncStore.isSet(key.name)

    // endregion

    private fun <T> trySerialize(key: PrefKey<T>, value: T): String? = try {
        key.serialize(value)
    } catch (e: Throwable) {
        UALog.e(e) { "Failed to serialize preference ${key.name}" }
        null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun tearDown() {
        syncStore.tearDown()
    }

    public companion object {

        /** Loads (or creates) the preference store backed by the on-disk database. @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun load(context: Context, configOptions: AirshipConfigOptions): PreferenceStore =
            PreferenceStore(PreferenceDataStore.loadDataStore(context, configOptions))

        /** Builds an in-memory store for tests. @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public fun inMemoryStore(context: Context): PreferenceStore =
            PreferenceStore(PreferenceDataStore.inMemoryStore(context))
    }
}
