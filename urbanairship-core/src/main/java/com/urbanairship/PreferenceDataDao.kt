/* Copyright Airship and Contributors */
package com.urbanairship

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data Access Object for the Preference Data table.
 *
 * Eager-path methods (non-suspend) are used by [PreferenceDataStore] on its serial dispatcher
 * and filter on `lazy = 0` where appropriate. Async-path methods (suspend, `*Row` naming) are
 * used by [com.urbanairship.preferences.AsyncPreferenceStore] for on-demand access and operate
 * on any row regardless of the `lazy` flag.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class PreferenceDataDao public constructor() {

    // region Eager (non-suspend) — used by PreferenceDataStore

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract fun upsert(entity: PreferenceData)

    @Query("DELETE FROM preferences WHERE (`_id` == :key)")
    public abstract fun delete(key: String)

    @Query("DELETE FROM preferences")
    public abstract fun deleteAll()

    /** All eager rows — rows flagged `lazy` are loaded on demand via the async path instead. */
    @Transaction
    @Query("SELECT * FROM preferences WHERE lazy = 0")
    public abstract fun queryEagerPreferences(): List<PreferenceData>

    /** Single-row lookup. Returns the row regardless of `lazy`; used by the fallback per-key load. */
    @Transaction
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    public abstract fun queryValue(key: String): PreferenceData

    /** Keys of all eager rows. Used by the fallback per-key load path. */
    @Transaction
    @Query("SELECT _id FROM preferences WHERE lazy = 0")
    public abstract fun queryEagerKeys(): List<String>

    // endregion

    // region Async (suspend) — used by AsyncPreferenceStore

    /** Returns the row under [key] or `null` if absent. */
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    public abstract suspend fun findRow(key: String): PreferenceData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract suspend fun saveRow(entity: PreferenceData)

    @Query("DELETE FROM preferences WHERE (`_id` == :key)")
    public abstract suspend fun deleteRow(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM preferences WHERE (`_id` == :key))")
    public abstract suspend fun containsRow(key: String): Boolean

    /** Flips a row's `lazy` flag to `true`. No-op if the row doesn't exist. */
    @Query("UPDATE preferences SET lazy = 1 WHERE (`_id` == :key)")
    public abstract suspend fun markLazy(key: String)

    // endregion
}
