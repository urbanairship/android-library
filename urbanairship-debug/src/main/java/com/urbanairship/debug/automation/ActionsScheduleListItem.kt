package com.urbanairship.debug.automation

import com.urbanairship.automation.Schedule
import com.urbanairship.automation.actions.Actions
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.iam.InAppMessage

class ActionsScheduleListItem(schedule: Schedule<Actions>) {
    val message = schedule.data

    val type = schedule.type

    val map = schedule.data.actionsMap.toFormattedJsonString()

    val id = schedule.id
}
