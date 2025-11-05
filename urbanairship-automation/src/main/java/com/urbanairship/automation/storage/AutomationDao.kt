/* Copyright Airship and Contributors */
package com.urbanairship.automation.storage

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Automation data access object
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class AutomationDao public constructor() {

    @Transaction
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    public abstract fun insert(entity: ScheduleEntity, entities: List<TriggerEntity>)

    @Delete
    public abstract fun delete(entity: ScheduleEntity)

    @Transaction
    @Query("SELECT * FROM schedules")
    public abstract fun getSchedules(): List<FullSchedule>

    @Transaction
    public open fun insert(entries: Collection<FullSchedule>) {
        entries.forEach(::insert)
    }

    public fun insert(entry: FullSchedule) {
        insert(entry.schedule, entry.triggers)
    }

    public fun delete(entry: FullSchedule) {
        delete(entry.schedule)
    }

    public fun deleteSchedules(entries: Collection<FullSchedule>) {
        entries.forEach(::delete)
    }
}
