/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.urbanairship.db.SuspendingBatchedQueryHelper.runBatched

@Dao
internal interface LedgerDao {

    /**
     * Inserts [events] as a single unit so a failure part way through leaves no
     * rows behind. Callers that retry a failed append rely on this: a partial
     * write would be re-appended on the next attempt and double-count.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<LedgerEventEntity>)

    @Query("SELECT * FROM ledger_events WHERE scheduleId = :scheduleId")
    suspend fun getEvents(scheduleId: String): List<LedgerEventEntity>

    @Query("SELECT * FROM ledger_events WHERE scheduleId = :scheduleId OR sharedId = :sharedId")
    suspend fun getEvents(scheduleId: String, sharedId: String): List<LedgerEventEntity>

    @Transaction
    suspend fun deleteEvents(scheduleIds: List<String>, sharedIds: List<String>) {
        if (scheduleIds.isNotEmpty()) {
            runBatched(scheduleIds) { deleteByScheduleIds(it) }
        }
        if (sharedIds.isNotEmpty()) {
            runBatched(sharedIds) { deleteBySharedIds(it) }
        }
    }

    @Query("DELETE FROM ledger_events WHERE scheduleId IN (:scheduleIds)")
    suspend fun deleteByScheduleIds(scheduleIds: Collection<String>)

    @Query("DELETE FROM ledger_events WHERE sharedId IN (:sharedIds)")
    suspend fun deleteBySharedIds(sharedIds: Collection<String>)
}
