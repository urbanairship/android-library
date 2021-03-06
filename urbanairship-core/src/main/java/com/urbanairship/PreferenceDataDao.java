/* Copyright Airship and Contributors */

package com.urbanairship;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

/**
 * Data Access Object for the Preference Data table.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class PreferenceDataDao {

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsert(@NonNull PreferenceData entity);

    @Query("DELETE FROM preferences WHERE (`_id` == :key)")
    public abstract void delete(@NonNull String key);

    @Query("DELETE FROM preferences")
    public abstract void deleteAll();

    @Transaction
    @Query("SELECT * FROM preferences")
    @NonNull
    public abstract List<PreferenceData> getPreferences();

    @Transaction
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    @NonNull
    public abstract PreferenceData queryValue(@NonNull String key);

    @Transaction
    @Query("SELECT _id FROM preferences")
    @NonNull
    public abstract List<String> queryKeys();
}
