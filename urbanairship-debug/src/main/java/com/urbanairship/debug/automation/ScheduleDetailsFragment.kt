/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import android.text.format.DateFormat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.navigation.Navigation
import com.urbanairship.automation.Audience
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.Trigger
import com.urbanairship.debug.R
import com.urbanairship.debug.utils.PendingResultLiveData
import com.urbanairship.iam.InAppMessage
import java.util.Date
import java.util.concurrent.TimeUnit

class ScheduleDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_SCHEDULE_ID = "scheduleId"
    }

    private fun navigateToTrigger(trigger: Trigger) {
        val args = Bundle()
        args.putParcelable(TriggersDetailsFragment.ARGUMENT_TRIGGER, trigger)
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppMessageDetailsFragment_to_inAppTriggersDetailsFragment, args)
    }

    private fun navigateToDisplayContent(message: InAppMessage) {
        val args = Bundle()
        args.putParcelable(DisplayContentDetailsFragment.ARGUMENT_MESSAGE, message)
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppMessageDetailsFragment_to_inAppDisplayContentDetailsFragment, args)
    }

    private fun navigateToAudience(audience: Audience) {
        val args = Bundle()
        args.putString(AudienceDetailsFragment.ARGUMENT_AUDIENCE, audience.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppMessageDetailsFragment_to_inAppAudienceDetailsFragment, args)
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        val scheduleId = requireArguments().getString(ARGUMENT_SCHEDULE_ID)!!
        val scheduleLiveData = PendingResultLiveData<Schedule<InAppMessage>>(InAppAutomation.shared().getMessageSchedule(scheduleId))
        return Transformations.map(scheduleLiveData) { schedule ->
            detailsForSchedule(schedule)
        }
    }

    private fun detailsForSchedule(schedule: Schedule<InAppMessage>): List<AutomationDetail> {
        val message = schedule.data
        val dateFormat = DateFormat.getLongDateFormat(requireContext())

        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_message_name_key), message.name.orEmpty()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_message_id_key), message.id))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_message_display_type_key), message.type.capitalize()) {
                navigateToDisplayContent(message)
            })

            schedule.audience?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_audience_key)) {
                    navigateToAudience(it)
                })
            }

            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_id_key), schedule.id))

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
