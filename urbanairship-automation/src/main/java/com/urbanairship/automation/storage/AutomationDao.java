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

    @Delete
    public abstract void delete(@NonNull ScheduleEntity entity);

    @Transaction
    @Query("SELECT * FROM schedules")
    @NonNull
    public abstract List<FullSchedule> getSchedules();

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
