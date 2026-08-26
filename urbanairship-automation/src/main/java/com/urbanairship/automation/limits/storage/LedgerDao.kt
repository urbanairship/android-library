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

    /**
     * Fetches the events recorded under [scheduleId] or [sharedId], oldest
     * first. A null [sharedId] matches no row — `sharedId = NULL` is never true
     * in SQL — which narrows the query to the schedule's own events. Ties on
     * timestamp fall back to insert order.
     */
    @Query(
        "SELECT * FROM ledger_events " +
            "WHERE scheduleId = :scheduleId OR sharedId = :sharedId " +
            "ORDER BY timestamp ASC, id ASC"
    )
    suspend fun getEvents(scheduleId: String, sharedId: String?): List<LedgerEventEntity>

    /**
     * True if any event is recorded under [scheduleId]. Answered from the
     * indexed column, so no body is decoded.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM ledger_events WHERE scheduleId = :scheduleId)")
    suspend fun hasEvents(scheduleId: String): Boolean

    @Transaction
    suspend fun deleteEvents(scheduleIds: List<String>, sharedIds: List<String>) {
        if (scheduleIds.isNotEmpty()) {
            runBatched(scheduleIds) { deleteByScheduleIdsBatchInternal(it) }
        }
        if (sharedIds.isNotEmpty()) {
            runBatched(sharedIds) { deleteBySharedIdsBatchInternal(it) }
        }
    }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("DELETE FROM ledger_events WHERE scheduleId IN (:scheduleIds)")
    suspend fun deleteByScheduleIdsBatchInternal(scheduleIds: Collection<String>)

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("DELETE FROM ledger_events WHERE sharedId IN (:sharedIds)")
    suspend fun deleteBySharedIdsBatchInternal(sharedIds: Collection<String>)
}
