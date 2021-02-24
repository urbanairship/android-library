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
import com.urbanairship.automation.deferred.Deferred
import com.urbanairship.debug.R
import com.urbanairship.debug.utils.PendingResultLiveData
import java.util.Date
import java.util.concurrent.TimeUnit

class DeferredScheduleDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_SCHEDULE_ID = "id"
    }

    private fun navigateToAudience(audience: Audience) {
        val args = Bundle()
        args.putString(AudienceDetailsFragment.ARGUMENT_AUDIENCE, audience.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_deferredScheduleDetailsFragment_to_audienceDetailsFragment, args)
    }

    private fun navigateToTrigger(trigger: Trigger) {
        val args = Bundle()
        args.putParcelable(TriggersDetailsFragment.ARGUMENT_TRIGGER, trigger)
        Navigation.findNavController(requireView())
                .navigate(R.id.action_deferredScheduleDetailsFragment_to_triggersDetailsFragment, args)
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        Log.d("FormattedJson", requireArguments().getString(ARGUMENT_SCHEDULE_ID)!!)
        val scheduleId = requireArguments().getString(ARGUMENT_SCHEDULE_ID)!!
        val scheduleLiveData = PendingResultLiveData<Schedule<Deferred>>(InAppAutomation.shared().getDeferredMessageSchedule(scheduleId))
        return Transformations.map(scheduleLiveData) { schedule ->
            detailsForSchedule(schedule)
        }
    }

    private fun detailsForSchedule(schedule: Schedule<Deferred>): List<AutomationDetail> {
        val dateFormat = DateFormat.getLongDateFormat(requireContext())
        val message = schedule.data

        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_url_key), message.url.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_id_key), schedule.id))

            schedule.audience?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_audience_key)) {
                    navigateToAudience(it)
                })
            }

            if (schedule.start >= 0) {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_start_key), dateFormat.format(Date(schedule.start))))
            }

            if (schedule.end >= 0) {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_end_key), dateFormat.format(Date(schedule.end))))
            }

            add(AutomationDetail(getString(R.string.ua_iaa_debug_priority_key), schedule.priority.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_limit_key), schedule.limit.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_edit_grace_period_key), schedule.editGracePeriod.formatDuration(requireContext(), TimeUnit.MILLISECONDS)))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_interval_key), schedule.interval.formatDuration(requireContext(), TimeUnit.MILLISECONDS)))

            schedule.triggers.forEach {
                add(AutomationDetail(it.triggerTitle(requireContext())) {
                    navigateToTrigger(it)
                })
            }
        }
    }
}
