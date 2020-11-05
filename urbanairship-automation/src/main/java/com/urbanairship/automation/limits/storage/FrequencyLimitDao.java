/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public interface FrequencyLimitDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(@NonNull ConstraintEntity constraint);

    @Update
    void update(@NonNull ConstraintEntity constraint);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(@NonNull OccurrenceEntity occurrence);

    @Query("SELECT * FROM constraints WHERE (constraintId IN (:constraintIds))")
    List<ConstraintEntity> getConstraints(Collection<String> constraintIds);

    @Query("SELECT * FROM constraints")
    List<ConstraintEntity> getConstraints();

    @Query("SELECT * FROM occurrences WHERE parentConstraintId = :constraintId ORDER BY timeStamp ASC")
    List<OccurrenceEntity> getOccurrences(String constraintId);

    @Delete
    @Transaction
    void delete(ConstraintEntity entity);

    @Query("DELETE FROM constraints WHERE (constraintId IN (:constraintIds))")
    @Transaction
    void delete(Collection<String> constraintIds);

}
