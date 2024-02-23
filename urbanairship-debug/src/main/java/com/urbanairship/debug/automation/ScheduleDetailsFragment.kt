/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.navigation.Navigation
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.ScheduleData
import com.urbanairship.automation.Trigger
import com.urbanairship.automation.actions.Actions
import com.urbanairship.automation.deferred.Deferred
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString
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

    private fun navigateToAudience(audience: AudienceSelector) {
        val args = Bundle()
        args.putString(AudienceDetailsFragment.ARGUMENT_AUDIENCE, audience.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppMessageDetailsFragment_to_inAppAudienceDetailsFragment, args)
    }

    private fun navigateToScheduleData(scheduleData: ScheduleData, scheduleId: String) {
        when (scheduleData) {
            is InAppMessage -> {
                val args = bundleOf(MessageDetailsFragment.ARGUMENT_SCHEDULE to scheduleData)
                Navigation.findNavController(requireView()).navigate(R.id.action_inAppMessageDetailsFragment_to_inAppDisplayContentDetailsFragment, args)
            }
            is Deferred -> {
                val args = bundleOf(DeferredScheduleDetailsFragment.ARGUMENT_SCHEDULE_ID to scheduleId)
                Navigation.findNavController(requireView()).navigate(R.id.deferredScheduleDetailsFragment, args)
            }
            is Actions -> {
                val args = bundleOf(ActionsScheduleDetailsFragment.ARGUMENT_SCHEDULE_ID to scheduleId)
                Navigation.findNavController(requireView()).navigate(R.id.actionsScheduleDetailsFragment, args)
            }
        }
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        val scheduleId = requireArguments().getString(ARGUMENT_SCHEDULE_ID)!!
        val scheduleLiveData = PendingResultLiveData<Schedule<out ScheduleData>>(InAppAutomation.shared().getSchedule(scheduleId))
        return scheduleLiveData.map { schedule ->
            detailsForSchedule(schedule)
        }
    }

    private fun detailsForSchedule(schedule: Schedule<out ScheduleData>): List<AutomationDetail> {
        val dateFormat = DateFormat.getLongDateFormat(requireContext())

        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_schedule_id_key), schedule.id))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_data_key)) {
                navigateToScheduleData(schedule.data, schedule.id)
            })

            schedule.audienceSelector?.let {
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

            add(AutomationDetail(getString(R.string.ua_iaa_debug_payload_key), schedule.data.toJsonValue().toFormattedJsonString()))
        }
    }
}
