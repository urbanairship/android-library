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
    public abstract void insert(@NonNull PreferenceData entity);

    @Transaction
    @Update
    public abstract void update(@NonNull PreferenceData entity);

    @Transaction
    @Update
    public abstract void updatePreferences(@NonNull List<PreferenceData> entities);

    @Delete
    public abstract void delete(@NonNull PreferenceData entity);

    @Query("DELETE FROM preferences")
    public abstract void deleteAll();

    @Query("SELECT COUNT(*) FROM preferences")
    public abstract int getPreferencesCount();

    @Transaction
    @Query("SELECT * FROM preferences")
    @NonNull
    public abstract List<PreferenceData> getPreferences();

    @Transaction
    @Query("SELECT * FROM preferences")
    @NonNull
    public abstract LiveData<List<PreferenceData>> getLiveDataPreferences();

    @Transaction
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    @NonNull
    public abstract PreferenceData queryValue(@NonNull String key);

    @Transaction
    @Query("SELECT * FROM preferences WHERE (`_id` == :key)")
    @NonNull
    public abstract LiveData<PreferenceData> queryLiveDataValue(@NonNull String key);

    @Transaction
    @Query("SELECT _id FROM preferences")
    @NonNull
    public abstract List<String> queryKeys();
}
