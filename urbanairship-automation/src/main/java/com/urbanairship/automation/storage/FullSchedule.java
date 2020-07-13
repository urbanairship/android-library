package com.urbanairship.automation.storage;

import java.util.List;

import androidx.annotation.RestrictTo;
import androidx.room.Embedded;
import androidx.room.Ignore;
import androidx.room.Relation;

/**
 * Contains schedule and triggers entities.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FullSchedule {

    @Embedded
    public ScheduleEntity schedule;

    @Relation(
            parentColumn = "scheduleId",
            entityColumn = "parentScheduleId"
    )
    public List<TriggerEntity> triggers;

    public FullSchedule(ScheduleEntity schedule, List<TriggerEntity> triggers) {
        this.schedule = schedule;
        this.triggers = triggers;
    }

    @Ignore
    @Override
    public String toString() {
        return "FullSchedule{" +
                "schedule=" + schedule +
                ", triggers=" + triggers +
                '}';
    }

}
