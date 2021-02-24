package com.urbanairship.debug.automation

import com.urbanairship.automation.Schedule
import com.urbanairship.automation.deferred.Deferred

class DeferredScheduleListItem(schedule: Schedule<Deferred>) {
    val message = schedule.data

    val type = schedule.type

    val id = schedule.id
}