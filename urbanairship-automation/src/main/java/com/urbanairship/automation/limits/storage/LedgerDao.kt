/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.urbanairship.db.SuspendingBatchedQueryHelper.runBatched

/**
 * The maximum number of host parameters SQLite will bind in one statement.
 * See: https://sqlite.org/limits.html#max_variable_number
 */
private const val MAX_STATEMENT_PARAMETERS = 999

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

    /**
     * The total number of stored rows, including rows whose body cannot be
     * decoded. Answered from the table, so no body is loaded.
     */
    @Query("SELECT COUNT(*) FROM ledger_events")
    suspend fun count(): Int

    /**
     * The smallest stored timestamp, or null when the ledger is empty. Answered
     * from the indexed column, so no body is loaded.
     */
    @Query("SELECT MIN(timestamp) FROM ledger_events")
    suspend fun oldestTimestamp(): Long?

    /** Every stored event, oldest first. Ties on timestamp fall back to insert order. */
    @Query("SELECT * FROM ledger_events ORDER BY timestamp ASC, id ASC")
    suspend fun getAllEvents(): List<LedgerEventEntity>

    /**
     * The recording scope of every stored row. Projects only the scope columns,
     * so retention can test rows for orphanhood without loading any body.
     */
    @Query("SELECT id, scheduleId, sharedId FROM ledger_events")
    suspend fun getEventScopes(): List<LedgerEventScope>

    /**
     * Deletes every event orphaned on both axes: its recording schedule is not
     * in [liveScheduleIds] **and** its shared group, if it has one, is not in
     * [liveSharedIds].
     *
     * Picks its own strategy. While the live IDs fit inside the statement
     * variable limit this is a single predicate delete that reads no rows.
     * Past that limit the predicate cannot be bound, and `NOT IN` cannot be
     * batched the way `IN` can — a chunk would delete the rows another chunk
     * keeps — so the orphans are identified from the scope columns instead and
     * deleted by primary key in batches. Either way the whole pass is one
     * transaction.
     *
     * @return the number of rows deleted.
     */
    @Transaction
    suspend fun deleteOrphanedEvents(
        liveScheduleIds: Set<String>,
        liveSharedIds: Set<String>
    ): Int {
        if (liveScheduleIds.size + liveSharedIds.size <= MAX_STATEMENT_PARAMETERS) {
            return deleteOrphanedEventsInternal(liveScheduleIds, liveSharedIds)
        }

        val orphanIds = getEventScopes()
            .filter { scope ->
                scope.scheduleId !in liveScheduleIds &&
                        (scope.sharedId == null || scope.sharedId !in liveSharedIds)
            }
            .map { it.id }

        var deleted = 0
        runBatched(orphanIds) { deleted += deleteByIdsBatchInternal(it) }
        return deleted
    }

    /**
     * Swaps the rows with the given primary keys for [events] as a single unit,
     * so a failure part way through can never leave the ledger holding the
     * pre-merge rows minus the ones already deleted. Rows outside [deleteIds]
     * are untouched, keeping their identity.
     */
    @Transaction
    suspend fun replaceEvents(deleteIds: List<Int>, events: List<LedgerEventEntity>) {
        runBatched(deleteIds) { deleteByIdsBatchInternal(it) }
        insertAll(events)
    }

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

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("DELETE FROM ledger_events WHERE id IN (:ids)")
    suspend fun deleteByIdsBatchInternal(ids: Collection<Int>): Int

    /**
     * This query is only for internal use, by [deleteOrphanedEvents], which
     * keeps the bound live IDs inside the statement variable limit.
     *
     * `sharedId IS NULL` is tested explicitly so rows with no shared group are
     * judged on their schedule alone rather than falling into SQL's
     * three-valued logic. SQLite evaluates `x NOT IN ()` as true, so empty live
     * sets correctly orphan everything.
     */
    @Query(
        "DELETE FROM ledger_events " +
            "WHERE scheduleId NOT IN (:liveScheduleIds) " +
            "AND (sharedId IS NULL OR sharedId NOT IN (:liveSharedIds))"
    )
    suspend fun deleteOrphanedEventsInternal(
        liveScheduleIds: Collection<String>,
        liveSharedIds: Collection<String>
    ): Int
}
