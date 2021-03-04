package com.urbanairship.debug.automation

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.navigation.Navigation
import com.urbanairship.automation.Audience
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.Trigger
import com.urbanairship.automation.actions.Actions
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.debug.utils.PendingResultLiveData
import java.util.Date
import java.util.concurrent.TimeUnit

class ActionsScheduleDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_SCHEDULE_ID = "scheduleId"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        val scheduleId = requireArguments().getString(ARGUMENT_SCHEDULE_ID)!!
        val scheduleLiveData = PendingResultLiveData<Schedule<Actions>>(InAppAutomation.shared().getActionSchedule(scheduleId))
        return Transformations.map(scheduleLiveData) { schedule ->
            detailsForSchedule(schedule)
        }
    }

    private fun detailsForSchedule(schedule: Schedule<Actions>): List<AutomationDetail> {

        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_actions), schedule.data.actionsMap.toFormattedJsonString()))
        }
    }
}
