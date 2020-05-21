/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import android.text.format.DateFormat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.navigation.Navigation
import com.urbanairship.automation.Trigger
import com.urbanairship.debug.R
import com.urbanairship.debug.utils.PendingResultLiveData
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.InAppMessageManager
import com.urbanairship.iam.InAppMessageSchedule

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

    private fun navigateToAudience(message: InAppMessage) {
        val args = Bundle()
        args.putParcelable(AudienceDetailsFragment.ARGUMENT_MESSAGE, message)
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppMessageDetailsFragment_to_inAppAudienceDetailsFragment, args)
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        val scheduleId = requireArguments().getString(ARGUMENT_SCHEDULE_ID)!!
        val scheduleLiveData = PendingResultLiveData<InAppMessageSchedule>(InAppMessageManager.shared().getSchedule(scheduleId))
        return Transformations.map(scheduleLiveData) { schedule ->
            detailsForSchedule(schedule)
        }
    }

    private fun detailsForSchedule(schedule: InAppMessageSchedule): List<AutomationDetail> {
        val scheduleInfo = schedule.info
        val message = scheduleInfo.inAppMessage
        val dateFormat = DateFormat.getDateFormat(requireContext())

        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_message_name_key), message.name.orEmpty()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_message_id_key), message.id))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_message_display_type_key), message.type.capitalize()) {
                navigateToDisplayContent(message)
            })
            add(AutomationDetail(getString(R.string.ua_iaa_debug_audience_key)) {
                navigateToAudience(message)
            })
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_id_key), schedule.id))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_start_key), dateFormat.format(schedule.info.start)))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_end_key), dateFormat.format(schedule.info.end)))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_priority_key), scheduleInfo.priority.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_limit_key), scheduleInfo.limit.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_edit_grace_period_key), scheduleInfo.editGracePeriod.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_interval_key), scheduleInfo.interval.toString()))
            scheduleInfo.triggers.forEach {
                add(AutomationDetail(it.triggerTitle(requireContext())) {
                    navigateToTrigger(it)
                })
            }
        }
    }
}
