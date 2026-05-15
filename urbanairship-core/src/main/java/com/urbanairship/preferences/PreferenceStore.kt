/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PreferenceDataStore

/**
 * Container for Airship preference accessors.
 *
 * Typed access via [SyncPrefKey] / [AsyncPrefKey] is the preferred API. Sync keys resolve to
 * regular calls; async keys resolve to `suspend` calls. Each key carries its own
 * serialize/deserialize logic, so the store itself just stores and reads strings.
 *
 * The legacy [sync] accessor exposing the raw [PreferenceDataStore] is retained for call sites
 * that haven't migrated yet.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    public val sync: PreferenceDataStore
) {

    // region Sync typed access

    public fun <T> get(key: SyncPrefKey<T>): T? =
        sync.getString(key.name, null)?.let(key::deserialize)

    public fun <T> put(key: SyncPrefKey<T>, value: T?) {
        if (value == null) sync.remove(key.name) else sync.put(key.name, key.serialize(value))
    }

    public fun remove(key: SyncPrefKey<*>) {
        sync.remove(key.name)
    }

    public fun isSet(key: SyncPrefKey<*>): Boolean = sync.isSet(key.name)

    // endregion

    // region Async typed access (suspend)

    public suspend fun <T> get(key: AsyncPrefKey<T>): T? =
        sync.getString(key.name, null)?.let(key::deserialize)

    public suspend fun <T> put(key: AsyncPrefKey<T>, value: T?) {
        if (value == null) sync.remove(key.name) else sync.put(key.name, key.serialize(value))
    }

    public suspend fun remove(key: AsyncPrefKey<*>) {
        sync.remove(key.name)
    }

    public suspend fun isSet(key: AsyncPrefKey<*>): Boolean = sync.isSet(key.name)

    // endregion

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun tearDown() {
        sync.tearDown()
    }

    public companion object {

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun load(context: Context, configOptions: AirshipConfigOptions): PreferenceStore =
            PreferenceStore(PreferenceDataStore.loadDataStore(context, configOptions))

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public fun inMemoryStore(context: Context): PreferenceStore =
            PreferenceStore(PreferenceDataStore.inMemoryStore(context))
    }
}
