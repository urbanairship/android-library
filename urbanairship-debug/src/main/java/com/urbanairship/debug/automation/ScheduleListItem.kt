/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.content.Context
import androidx.core.content.ContextCompat
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.ScheduleData
import com.urbanairship.automation.actions.Actions
import com.urbanairship.automation.deferred.Deferred
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.iam.InAppMessage

class ScheduleListItem(schedule: Schedule<out ScheduleData>) {

    val name: String? = {
        when (schedule.type) {
            Schedule.TYPE_IN_APP_MESSAGE -> (schedule.data as InAppMessage).name
            Schedule.TYPE_ACTION -> (schedule.data as Actions).actionsMap.toFormattedJsonString()
            Schedule.TYPE_DEFERRED -> (schedule.data as Deferred).url.toString()
            else -> ""
        }
    }.invoke()

    val type: String? = {
        if (schedule.type == Schedule.TYPE_IN_APP_MESSAGE) {
            (schedule.data as InAppMessage).type
        } else {
            schedule.type
        }
    }.invoke()

    val id = schedule.id

    fun getColor(context: Context): Int {
        val colorRes = when (type) {
            InAppMessage.TYPE_BANNER -> R.color.ua_banner_iaa
            InAppMessage.TYPE_FULLSCREEN -> R.color.ua_fullscreen_iaa
            InAppMessage.TYPE_MODAL -> R.color.ua_modal_iaa
            InAppMessage.TYPE_HTML -> R.color.ua_html_iaa
            InAppMessage.TYPE_CUSTOM -> R.color.ua_custom_iaa
            Schedule.TYPE_DEFERRED -> R.color.ua_deferred_iaa
            Schedule.TYPE_ACTION -> R.color.ua_action_schedule
            else -> R.color.ua_custom_iaa
        }

        return ContextCompat.getColor(context, colorRes)
    }
}
