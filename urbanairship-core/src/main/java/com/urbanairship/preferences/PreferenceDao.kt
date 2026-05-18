/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data Access Object for the preferences table.
 *
 * All methods are `suspend` — Room dispatches them to its internal IO executor. Eager-load
 * methods filter on `lazy = 0`; the others operate on any row regardless of flag.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class PreferenceDao public constructor() {

    /** All eager rows. Used by [EagerPreferenceStore] at takeoff. */
    @Transaction
    @Query("SELECT * FROM preferences WHERE lazy = 0")
    public abstract suspend fun queryEagerPreferences(): List<PreferenceData>

    /** Keys of all eager rows. Used by the fallback per-key load path. */
    @Transaction
    @Query("SELECT _id FROM preferences WHERE lazy = 0")
    public abstract suspend fun queryEagerKeys(): List<String>

    /** Single-row lookup. Returns `null` if absent. */
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    public abstract suspend fun findRow(key: String): PreferenceData?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract suspend fun upsert(entity: PreferenceData)

    @Query("DELETE FROM preferences WHERE (`_id` == :key)")
    public abstract suspend fun delete(key: String)

    @Query("DELETE FROM preferences")
    public abstract suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM preferences WHERE (`_id` == :key))")
    public abstract suspend fun contains(key: String): Boolean

    /** Flips a row's `lazy` flag to `true`. No-op if the row doesn't exist. */
    @Query("UPDATE preferences SET lazy = 1 WHERE (`_id` == :key)")
    public abstract suspend fun markLazy(key: String)
}
