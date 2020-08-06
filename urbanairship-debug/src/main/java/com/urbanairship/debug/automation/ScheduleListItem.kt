/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import com.urbanairship.automation.Schedule
import com.urbanairship.iam.InAppMessage

class ScheduleListItem(schedule: Schedule<InAppMessage>) {
    val message = schedule.data

    val type = message.type

    val id = message.id
}
