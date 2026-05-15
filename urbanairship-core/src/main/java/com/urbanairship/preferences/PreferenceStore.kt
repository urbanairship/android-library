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
 * The legacy [sync] accessor exposing the raw [PreferenceDataStore] is retained for call sites
 * that haven't migrated yet.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    /**
     * Direct access to the underlying eager [PreferenceDataStore]. Prefer the typed [get] /
     * [put] overloads; this accessor is retained for call sites that haven't migrated to typed
     * keys.
     */
    public val sync: PreferenceDataStore,
    private val async: AsyncPreferenceStore = AsyncPreferenceStore(sync.dao)
) {

    // region Sync typed access

    /**
     * Returns the value stored under [key], or `null` if the key is unset, the stored value
     * cannot be deserialized, or deserialization throws (errors are logged).
     */
    public fun <T> get(key: SyncPrefKey<T>): T? {
        val stored = sync.getString(key.name, null) ?: return null
        return tryDeserialize(key, stored)
    }

    /**
     * Stores [value] under [key]. Passing `null` removes the key. If [key]'s serializer throws,
     * the error is logged and the write is dropped.
     */
    public fun <T> put(key: SyncPrefKey<T>, value: T?) {
        if (value == null) {
            sync.remove(key.name)
            return
        }
        val serialized = trySerialize(key, value) ?: return
        sync.put(key.name, serialized)
    }

    /** Removes [key]. */
    public fun remove(key: SyncPrefKey<*>) {
        sync.remove(key.name)
    }

    /** Returns `true` if [key] has any stored value (even one that fails to deserialize). */
    public fun isSet(key: SyncPrefKey<*>): Boolean = sync.isSet(key.name)

    // endregion

    // region Async typed access (suspend)

    /**
     * Returns the value stored under [key], or `null` if the key is unset, the stored value
     * cannot be deserialized, or deserialization throws (errors are logged). The row is
     * self-migrated to `lazy = true` on first access so future takeoffs skip it.
     */
    public suspend fun <T> get(key: AsyncPrefKey<T>): T? {
        val stored = async.getString(key.name) ?: return null
        return tryDeserialize(key, stored)
    }

    /**
     * Stores [value] under [key] with `lazy = true`. Passing `null` removes the key. If [key]'s
     * serializer throws, the error is logged and the write is dropped.
     */
    public suspend fun <T> put(key: AsyncPrefKey<T>, value: T?) {
        if (value == null) {
            async.remove(key.name)
            return
        }
        val serialized = trySerialize(key, value) ?: return
        async.put(key.name, serialized)
    }

    /** Removes [key]. */
    public suspend fun remove(key: AsyncPrefKey<*>) {
        async.remove(key.name)
    }

    /** Returns `true` if [key] has any stored value (even one that fails to deserialize). */
    public suspend fun isSet(key: AsyncPrefKey<*>): Boolean = async.isSet(key.name)

    // endregion

    private fun <T> tryDeserialize(key: PrefKey<T>, stored: String): T? = try {
        key.deserialize(stored)
    } catch (e: Throwable) {
        UALog.e(e) { "Failed to deserialize preference ${key.name}" }
        null
    }

    private fun <T> trySerialize(key: PrefKey<T>, value: T): String? = try {
        key.serialize(value)
    } catch (e: Throwable) {
        UALog.e(e) { "Failed to serialize preference ${key.name}" }
        null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun tearDown() {
        sync.tearDown()
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
