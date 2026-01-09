package com.urbanairship.automation.storage

import androidx.annotation.RestrictTo
import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.Relation

/**
 * Contains schedule and triggers entities.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FullSchedule public constructor(
    @field:Embedded
    public var schedule: ScheduleEntity,

    @field:Relation(parentColumn = "scheduleId", entityColumn = "parentScheduleId")
    public var triggers: List<TriggerEntity>
) {

    @Ignore
    override fun toString(): String {
        return "FullSchedule { schedule=$schedule, triggers=$triggers }"
    }
}
