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
 * regular calls; async keys resolve to `suspend` calls. Each key carries its own
 * serialize/deserialize logic, so the store itself just stores and reads strings.
 *
 * Serialization errors are handled here, not in the keys: a `serialize` that throws on [put]
 * is logged and the write is dropped; a `deserialize` that throws on [get] is logged and the
 * key is treated as unset.
 *
 * The legacy [sync] accessor exposing the raw [PreferenceDataStore] is retained for call sites
 * that haven't migrated yet.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    /**
     * Direct access to the underlying [PreferenceDataStore]. Prefer the typed [get] / [put]
     * overloads; this accessor is retained for call sites that haven't migrated to typed keys.
     */
    public val sync: PreferenceDataStore
) {

    // region Sync typed access

    /**
     * Returns the value stored under [key], or `null` if the key is unset, the stored value
     * cannot be deserialized, or deserialization throws (errors are logged).
     */
    public fun <T> get(key: SyncPrefKey<T>): T? = readTyped(key)

    /**
     * Stores [value] under [key]. Passing `null` removes the key. If [key]'s serializer throws,
     * the error is logged and the write is dropped.
     */
    public fun <T> put(key: SyncPrefKey<T>, value: T?): Unit = writeTyped(key, value)

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
     * cannot be deserialized, or deserialization throws (errors are logged).
     */
    public suspend fun <T> get(key: AsyncPrefKey<T>): T? = readTyped(key)

    /**
     * Stores [value] under [key]. Passing `null` removes the key. If [key]'s serializer throws,
     * the error is logged and the write is dropped.
     */
    public suspend fun <T> put(key: AsyncPrefKey<T>, value: T?): Unit = writeTyped(key, value)

    /** Removes [key]. */
    public suspend fun remove(key: AsyncPrefKey<*>) {
        sync.remove(key.name)
    }

    /** Returns `true` if [key] has any stored value (even one that fails to deserialize). */
    public suspend fun isSet(key: AsyncPrefKey<*>): Boolean = sync.isSet(key.name)

    // endregion

    private fun <T> readTyped(key: PrefKey<T>): T? {
        val stored = sync.getString(key.name, null) ?: return null
        return try {
            key.deserialize(stored)
        } catch (e: Throwable) {
            UALog.e(e) { "Failed to deserialize preference ${key.name}" }
            null
        }
    }

    private fun <T> writeTyped(key: PrefKey<T>, value: T?) {
        if (value == null) {
            sync.remove(key.name)
            return
        }
        val serialized = try {
            key.serialize(value)
        } catch (e: Throwable) {
            UALog.e(e) { "Failed to serialize preference ${key.name}" }
            return
        }
        sync.put(key.name, serialized)
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
