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
import com.urbanairship.util.UAStringUtil

/**
 * Event data access object.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
internal abstract class EventDao public constructor() {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract fun insert(event: EventEntity)

    @Transaction
    @Query("SELECT * FROM events ORDER BY id ASC")
    public abstract fun get(): List<EventEntity>

    @Transaction
    @Query("SELECT id, eventId, data FROM events ORDER BY id ASC LIMIT :limit")
    public abstract fun getBatch(limit: Int): List<EventIdAndData>

    @Transaction
    public open fun deleteBatch(events: List<EventIdAndData>) {
        for (event in events) {
            delete(event.eventId)
        }
    }

    @Query("DELETE FROM events WHERE eventId = :eventId")
    public abstract fun delete(eventId: String)

    @Delete
    public abstract fun delete(vararg events: EventEntity)

    @Query("DELETE FROM events")
    public abstract fun deleteAll()

    @Query("SELECT COUNT(*) FROM events")
    public abstract fun count(): Int

    @Query("SELECT SUM(eventSize) FROM events")
    public abstract fun databaseSize(): Int

    @Query("SELECT sessionId FROM events ORDER BY id ASC LIMIT 1")
    public abstract fun oldestSessionId(): String?

    @Query("DELETE FROM events WHERE sessionId = :sessionId")
    public abstract fun deleteSession(sessionId: String): Int

    @Transaction
    public open fun trimDatabase(maxDatabaseSize: Int) {
        while (databaseSize() > maxDatabaseSize) {
            val sessionId = oldestSessionId() ?: return

            if (UAStringUtil.isEmpty(sessionId)) {
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
