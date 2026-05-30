/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.annotation.RestrictTo
import com.urbanairship.db.guardDao

/**
 * Thin shim over [PreferenceDao] for `AsyncPrefKey` access.
 *
 * Every read goes straight to the database (Room's `suspend` DAOs handle threading). Rows touched
 * here are marked `lazy = true` so the next takeoff skips them, migrating the row to the lazy
 * path on first async access.
 *
 * DAO failures are logged and degrade to the operation's default (`null` for reads, no-op for
 * writes, `false` for existence checks). [kotlinx.coroutines.CancellationException] is re-thrown so coroutine
 * cancellation propagates normally.
 *
 * No in-memory cache: SQLite's page cache covers the working set, and a separate cache would
 * introduce coherence risk with the eager [EagerPreferenceStore] for the brief window during
 * migration.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AsyncPreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    private val dao: PreferenceDao
) {

    /**
     * Reads the value under [key], returning `null` if unset. If the existing row was flagged
     * eager (`lazy = false`), flips it to `lazy = true` so future takeoffs skip the row.
     */
    public suspend fun getString(key: String): String? = guardDao("findRow($key)", null) {
        val row = dao.findRow(key) ?: return@guardDao null
        if (!row.lazy) dao.markLazy(key)
        row.value
    }

    /** Upserts [value] under [key] with `lazy = true`. */
    public suspend fun put(key: String, value: String): Unit = guardDao("upsert($key)", Unit) {
        dao.upsert(PreferenceData(key = key, value = value, lazy = true))
    }

    /** Removes [key]. */
    public suspend fun remove(key: String): Unit = guardDao("delete($key)", Unit) {
        dao.delete(key)
    }

    /** Returns `true` if [key] has any stored value. */
    public suspend fun isSet(key: String): Boolean = guardDao("contains($key)", false) {
        dao.contains(key)
    }
}
