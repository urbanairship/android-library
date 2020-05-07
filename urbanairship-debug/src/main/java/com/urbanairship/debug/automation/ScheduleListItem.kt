/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import com.urbanairship.iam.InAppMessageSchedule

class ScheduleListItem(inAppMessageSchedule: InAppMessageSchedule) {
    val message = inAppMessageSchedule.info.inAppMessage

    val type = message.type

    val id = message.id
}
