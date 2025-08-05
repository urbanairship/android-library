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
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class PreferenceDataDao public constructor() {

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract fun upsert(entity: PreferenceData)

    @Query("DELETE FROM preferences WHERE (`_id` == :key)")
    public abstract fun delete(key: String)

    @Query("DELETE FROM preferences")
    public abstract fun deleteAll()

    @Transaction
    @Query("SELECT * FROM preferences")
    public abstract fun getPreferences(): List<PreferenceData>

    @Transaction
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    public abstract fun queryValue(key: String): PreferenceData

    @Transaction
    @Query("SELECT _id FROM preferences")
    public abstract fun queryKeys(): List<String>
}
