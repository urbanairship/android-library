package com.urbanairship.analytics.data;

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

/**
 * Event data access object.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(EventEntity event);

    @Transaction
    @Query("SELECT * FROM events ORDER BY id ASC")
    public abstract List<EventEntity> get();

    @Transaction
    @Query("SELECT id, eventId, data FROM events ORDER BY id ASC LIMIT :limit")
    public abstract List<EventEntity.EventIdAndData> getBatch(int limit);

    @Transaction
    public void deleteBatch(List<EventEntity.EventIdAndData> events) {
        for (EventEntity.EventIdAndData event : events) {
            delete(event.eventId);
        }
    }

    @Query("DELETE FROM events WHERE eventId = :eventId")
    abstract void delete(String eventId);

    @Delete()
    public abstract void delete(EventEntity... events);

    @Query("DELETE FROM events")
    public abstract void deleteAll();

    @Query("SELECT COUNT(*) FROM events")
    public abstract int count();

    @Query("SELECT SUM(eventSize) FROM events")
    public abstract int databaseSize();

    @Query("SELECT sessionId FROM events ORDER BY id ASC LIMIT 1")
    abstract String oldestSessionId();

    @Query("DELETE FROM events WHERE sessionId = :sessionId")
    abstract int deleteSession(String sessionId);

    @Transaction
    public void trimDatabase(int maxDatabaseSize) {
        while (databaseSize() > maxDatabaseSize) {
            String sessionId = oldestSessionId();
            if (UAStringUtil.isEmpty(sessionId)) {
                return;
            }

            Logger.debug("Event database size exceeded. Deleting oldest session: %s", sessionId);

            int deleted = deleteSession(sessionId);
            Logger.debug("Deleted %d rows with session ID %s", deleted, sessionId);

            if (deleted == 0) {
                return;
            }
        }
    }
}
