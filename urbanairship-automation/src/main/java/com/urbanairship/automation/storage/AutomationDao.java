/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.Transaction;
import androidx.room.Update;

/**
 * Automation data access object
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class AutomationDao {

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(@NonNull ScheduleEntity entity, @NonNull List<TriggerEntity> entities);

    @Transaction
    @Update
    public abstract void update(@NonNull ScheduleEntity entity, @NonNull List<TriggerEntity> entities);

    @Transaction
    @Update
    public abstract void updateTriggers(@NonNull List<TriggerEntity> entities);

    @Delete
    public abstract void delete(@NonNull ScheduleEntity entity);

    @Query("SELECT COUNT(*) FROM schedules")
    public abstract int getScheduleCount();

    @Query("DELETE FROM schedules WHERE scheduleType = :scheduleType")
    abstract void deleteSchedulesByType(int scheduleType);

    @Transaction
    @Query("SELECT * FROM schedules")
    @NonNull
    public abstract List<FullSchedule> getSchedules();

    @Transaction
    @Query("SELECT * FROM schedules WHERE (scheduleType = :type)")
    @NonNull
    public abstract List<FullSchedule> getSchedulesByType(@NonNull String type);

    @Transaction
    @Query("SELECT * FROM schedules WHERE (scheduleId == :scheduleId)")
    @Nullable
    public abstract FullSchedule getSchedule(@NonNull String scheduleId);

    @Transaction
    @Query("SELECT * FROM schedules WHERE (scheduleId == :scheduleId) AND (scheduleType = :type)")
    @Nullable
    public abstract FullSchedule getSchedule(@NonNull String scheduleId, @NonNull String type);

    @Transaction
    @Query("SELECT * FROM schedules WHERE (scheduleId IN (:scheduleIds))")
    @NonNull
    public abstract List<FullSchedule> getSchedules(@NonNull Collection<String> scheduleIds);


    @Transaction
    @Query("SELECT * FROM schedules WHERE (scheduleId IN (:scheduleIds)) AND (scheduleType = :type)")
    @NonNull
    public abstract List<FullSchedule> getSchedules(@NonNull Collection<String> scheduleIds, @NonNull String type);

    @Transaction
    @Query("SELECT * FROM schedules WHERE (`group` == :group) AND (scheduleType = :type)")
    @NonNull
    public abstract List<FullSchedule> getSchedulesWithGroup(@NonNull String group, @NonNull String type);

    @Transaction
    @Query("SELECT * FROM schedules WHERE (`group` == :group)")
    @NonNull
    public abstract List<FullSchedule> getSchedulesWithGroup(@NonNull String group);

    @Transaction
    @Query("SELECT * FROM schedules WHERE (executionState IN (:executionStates))")
    @NonNull
    public abstract List<FullSchedule> getSchedulesWithStates(int... executionStates);

    @Transaction
    @Query("SELECT * FROM schedules " +
            "WHERE (executionState != " + ScheduleState.FINISHED + ") " +
            "AND (scheduleEnd >= 0) AND (scheduleEnd <= strftime('%s', 'now') * 1000)")
    @NonNull
    public abstract List<FullSchedule> getActiveExpiredSchedules();

    @Query("SELECT triggers.* FROM triggers " +
            "JOIN schedules ON schedules.scheduleId = triggers.parentScheduleId " +
            "WHERE (schedules.scheduleId = :scheduleId)" +
            "AND (triggers.triggerType = :type) " +
            "AND ((triggers.isCancellation = 1 AND + schedules.executionState IN (" + ScheduleState.WAITING_SCHEDULE_CONDITIONS + "," + ScheduleState.TIME_DELAYED + "," + ScheduleState.PREPARING_SCHEDULE + "))" +
            "OR (triggers.isCancellation = 0 AND + schedules.executionState = " + ScheduleState.IDLE + "))" +
            "AND (schedules.scheduleStart < 0 OR schedules.scheduleStart <= strftime('%s', 'now') * 1000)")
    @NonNull
    public abstract List<TriggerEntity> getActiveTriggers(int type, @NonNull String scheduleId);

    @Query("SELECT triggers.* FROM triggers " +
            "JOIN schedules ON schedules.scheduleId = triggers.parentScheduleId " +
            "AND (triggers.triggerType = :type) " +
            "AND ((triggers.isCancellation = 1 AND + schedules.executionState IN (" + ScheduleState.WAITING_SCHEDULE_CONDITIONS + "," + ScheduleState.TIME_DELAYED + "," + ScheduleState.PREPARING_SCHEDULE + "))" +
            "OR (triggers.isCancellation = 0 AND + schedules.executionState = " + ScheduleState.IDLE + "))" +
            "AND (schedules.scheduleStart < 0 OR schedules.scheduleStart <= strftime('%s', 'now') * 1000)")
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @NonNull
    public abstract List<TriggerEntity> getActiveTriggers(int type);

    @Transaction
    public void insert(@NonNull Collection<FullSchedule> entries) {
        for (FullSchedule entry : entries) {
            if (entry != null) {
                insert(entry);
            }
        }
    }

    public void insert(@NonNull FullSchedule entry) {
        insert(entry.schedule, entry.triggers);
    }

    public void updateSchedules(@NonNull Collection<FullSchedule> entries) {
        for (FullSchedule entry : entries) {
            if (entry != null) {
                update(entry);
            }
        }
    }

    public void update(@NonNull FullSchedule entry) {
        update(entry.schedule, entry.triggers);
    }

    public void delete(@NonNull FullSchedule entry) {
        delete(entry.schedule);
    }

    public void deleteSchedules(@NonNull Collection<FullSchedule> entries) {
        for (FullSchedule entry : entries) {
            if (entry != null) {
                delete(entry);
            }
        }
    }

}
