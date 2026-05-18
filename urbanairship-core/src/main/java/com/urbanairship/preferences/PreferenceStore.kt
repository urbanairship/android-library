/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.UALog
import kotlinx.coroutines.runBlocking

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
 * [PreferenceStore] owns the [PreferenceDatabase] lifecycle — both substores receive the [dao]
 * but don't own it.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    private val database: PreferenceDatabase,
    private val eagerStore: EagerPreferenceStore = EagerPreferenceStore(database.dao),
    private val asyncStore: AsyncPreferenceStore = AsyncPreferenceStore(database.dao)
) {

    /** Test-only window into the DAO for verifying lazy-column state. */
    @VisibleForTesting
    internal val dao: PreferenceDao get() = database.dao

    // region Sync typed access

    /**
     * Returns the value stored under [key], or `null` if the key is unset, the stored value
     * cannot be deserialized, or deserialization throws (errors are logged).
     */
    public fun <T> get(key: SyncPrefKey<T>): T? {
        val stored = eagerStore.get(key.name) ?: return null
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
            eagerStore.remove(key.name)
            return
        }
        val serialized = trySerialize(key, value) ?: return
        eagerStore.put(key.name, serialized)
    }

    /**
     * Blocking variant of [put] that waits for the underlying database write to commit and
     * returns `true` on success. Use this only when subsequent logic depends on the write being
     * durable — e.g., a migration that deletes the legacy key only if the new key was written.
     * Passing `null` removes the key (also blocking).
     */
    public fun <T> putSync(key: SyncPrefKey<T>, value: T?): Boolean {
        if (value == null) return eagerStore.putSync(key.name, null)
        val serialized = trySerialize(key, value) ?: return false
        return eagerStore.putSync(key.name, serialized)
    }

    /** Removes [key]. */
    public fun remove(key: SyncPrefKey<*>) {
        eagerStore.remove(key.name)
    }

    /** Returns `true` if [key] has any stored value (even one that fails to deserialize). */
    public fun isSet(key: SyncPrefKey<*>): Boolean = eagerStore.isSet(key.name)

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
        database.close()
    }

    private fun cleanupObsoleteKeys() {
        OBSOLETE_KEYS.forEach { eagerStore.remove(it) }
    }

    public companion object {

        /**
         * Keys from previous SDK versions that should be deleted on every startup. Cheap enough
         * to scrub at takeoff rather than tracking a migration version.
         */
        private val OBSOLETE_KEYS = arrayOf(
            "com.urbanairship.TAG_GROUP_HISTORIAN_RECORDS",
            "com.urbanairship.push.iam.PENDING_IN_APP_MESSAGE",
            "com.urbanairship.push.iam.AUTO_DISPLAY_ENABLED",
            "com.urbanairship.push.iam.LAST_DISPLAYED_ID",
            "com.urbanairship.nameduser.CHANGE_TOKEN_KEY",
            "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY",
            "com.urbanairship.iam.tags.TAG_PREFER_LOCAL_DATA_TIME",
            "com.urbanairship.chat.CHAT",
            "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME",
            "com.urbanairship.push.LAST_REGISTRATION_TIME",
            "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD",
            "com.urbanairship.remotedata.LAST_REFRESH_APP_VERSION",
            "com.urbanairship.remotedata.LAST_MODIFIED",
            "com.urbanairship.remotedata.LAST_REFRESH_TIME",
            "com.urbanairship.iam.data.last_payload_info",
            "com.urbanairship.iam.data.LAST_PAYLOAD_METADATA",
            "com.urbanairship.iam.data.contact_last_payload_info",
            "com.urbanairship.push.SOUND_ENABLED",
            "com.urbanairship.push.VIBRATE_ENABLED",
            "com.urbanairship.push.QUIET_TIME_ENABLED",
            "com.urbanairship.push.QUIET_TIME_INTERVAL"
        )

        /**
         * Loads (or creates) the preference store backed by the on-disk database.
         *
         * Wraps the suspending eager-store load in [runBlocking] because the current `takeOff`
         * path is non-suspend. Once `takeOff` is converted to a coroutine (planned follow-up),
         * this becomes a direct suspend call.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun load(context: Context, configOptions: AirshipConfigOptions): PreferenceStore =
            runBlocking {
                val database = PreferenceDatabase.createDatabase(context, configOptions)
                PreferenceStore(
                    database = database,
                    eagerStore = EagerPreferenceStore.load(database.dao)
                ).also { it.cleanupObsoleteKeys() }
            }

        /** Builds an in-memory store for tests. @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public fun inMemoryStore(context: Context): PreferenceStore =
            PreferenceStore(PreferenceDatabase.createInMemoryDatabase(context))
    }
}
