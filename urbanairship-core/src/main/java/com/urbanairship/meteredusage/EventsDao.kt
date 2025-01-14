/* Copyright Airship and Contributors */

package com.urbanairship.meteredusage

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.urbanairship.db.SuspendingBatchedQueryHelper.runBatched

/**
 * Data Access Object for the event table.
 * @hide
 */
@Dao
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface EventsDao {
    @Query("SELECT * FROM events")
    fun getAllEvents(): List<MeteredUsageEventEntity>

    @Query("SELECT * FROM events WHERE eventId = :id")
    fun getEventWithId(id: String): MeteredUsageEventEntity?

    @Insert
    fun addEvent(event: MeteredUsageEventEntity)

    @Query("DELETE FROM events WHERE eventId = :eventId")
    fun delete(eventId: String)

    @Transaction
    suspend fun deleteAll(eventIds: List<String>) {
        runBatched(eventIds) { deleteAllBatchInternal(eventIds) }
    }

    @Query("DELETE FROM events WHERE eventId IN (:eventIds)")
    fun deleteAllBatchInternal(eventIds: List<String>)

    @Query("DELETE FROM events")
    fun deleteAll()
}
