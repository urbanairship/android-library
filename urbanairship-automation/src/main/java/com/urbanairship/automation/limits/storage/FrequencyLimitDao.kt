/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.urbanairship.db.SuspendingBatchedQueryHelper.runBatched

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
    suspend fun getAllConstraints(): List<ConstraintEntity>

    @Query("SELECT * FROM occurrences WHERE parentConstraintId = :constraintId ORDER BY timeStamp ASC")
    suspend fun getOccurrences(constraintId: String): List<OccurrenceEntity>

    @Delete
    @Transaction
    suspend fun delete(entity: ConstraintEntity)

    @Transaction
    suspend fun delete(constraintIds: List<String>) {
        runBatched(constraintIds) { deleteConstraintsBatchedInternal(constraintIds) }
    }

    @Query("DELETE FROM constraints WHERE (constraintId IN (:constraintIds))")
    fun deleteConstraintsBatchedInternal(constraintIds: Collection<String>)

    @Transaction
    suspend fun deleteOccurrences(constraintIds: List<String>) {
        runBatched(constraintIds) { deleteOccurrencesBatchedInternal(constraintIds) }
    }

    @Query("DELETE FROM occurrences WHERE (parentConstraintId IN (:constraintIds))")
    suspend fun deleteOccurrencesBatchedInternal(constraintIds: Collection<String>)
}
