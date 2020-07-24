/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import com.urbanairship.automation.Schedule
import com.urbanairship.iam.InAppMessage

class ScheduleListItem(schedule: Schedule) {
    val message = schedule.requireData<InAppMessage>()

    val type = message.type

    val id = message.id
}
