/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.limits.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
internal interface FrequencyLimitDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(constraint: ConstraintEntity)

    @Update
    suspend fun update(constraint: ConstraintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(occurrence: OccurrenceEntity)

    @Query("SELECT * FROM constraints WHERE (constraintId = :constraintId )")
    suspend fun getConstraint(constraintId: String): ConstraintEntity?

    @Query("SELECT * FROM constraints")
    suspend fun getAllConstraints(): List<ConstraintEntity>?

    @Query("SELECT * FROM occurrences WHERE parentConstraintId = :constraintId ORDER BY timeStamp ASC")
    suspend fun getOccurrences(constraintId: String): List<OccurrenceEntity>?

    @Delete
    @Transaction
    suspend fun delete(entity: ConstraintEntity)

    @Query("DELETE FROM constraints WHERE (constraintId IN (:constraintIds))")
    @Transaction
    suspend fun delete(constraintIds: Collection<String>)

    @Query("DELETE FROM occurrences WHERE (parentConstraintId IN (:constraintIds))")
    @Transaction
    suspend fun deleteOccurrences(constraintIds: Collection<String>)
}
