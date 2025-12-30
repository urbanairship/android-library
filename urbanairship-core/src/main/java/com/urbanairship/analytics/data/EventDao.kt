package com.urbanairship.analytics.data

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.urbanairship.UALog
import com.urbanairship.analytics.data.EventEntity.EventIdAndData

/**
 * Event data access object.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
internal abstract class EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    internal abstract suspend fun insert(event: EventEntity)

    @Transaction
    @Query("SELECT * FROM events ORDER BY id ASC")
    internal abstract suspend fun get(): List<EventEntity>

    @Transaction
    @Query("SELECT id, eventId, data FROM events ORDER BY id ASC LIMIT :limit")
    internal abstract suspend fun getBatch(limit: Int): List<EventIdAndData>

    @Transaction
    internal open suspend fun deleteBatch(events: List<EventIdAndData>) {
        for (event in events) {
            delete(event.eventId)
        }
    }

    @Query("DELETE FROM events WHERE eventId = :eventId")
    internal abstract suspend fun delete(eventId: String)

    @Delete
    internal abstract suspend fun delete(vararg events: EventEntity)

    @Query("DELETE FROM events")
    internal abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM events")
    internal abstract suspend fun count(): Int

    @Query("SELECT SUM(eventSize) FROM events")
    internal abstract suspend fun databaseSize(): Int

    @Query("SELECT sessionId FROM events ORDER BY id ASC LIMIT 1")
    internal abstract suspend fun oldestSessionId(): String?

    @Query("DELETE FROM events WHERE sessionId = :sessionId")
    internal abstract suspend fun deleteSession(sessionId: String): Int

    @Transaction
    internal open suspend fun trimDatabase(maxDatabaseSize: Int) {
        while (databaseSize() > maxDatabaseSize) {
            val sessionId = oldestSessionId() ?: return

            if (sessionId.isEmpty()) {
                return
            }

            UALog.d("Event database size exceeded. Deleting oldest session: $sessionId", )

            val deleted = deleteSession(sessionId)
            UALog.d("Deleted $deleted rows with session ID $sessionId")

            if (deleted == 0) {
                return
            }
        }
    }
}
