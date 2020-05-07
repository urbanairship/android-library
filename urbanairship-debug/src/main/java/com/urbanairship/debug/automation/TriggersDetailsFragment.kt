/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.urbanairship.automation.Trigger
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString

class TriggersDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_TRIGGER = "trigger"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        val trigger: Trigger = requireArguments().getParcelable(ARGUMENT_TRIGGER)!!
        return MutableLiveData(triggerDetails(trigger))
    }

    private fun triggerDetails(trigger: Trigger): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(trigger.triggerTitle(requireContext()), getString(R.string.ua_debug_trigger_goal_format, trigger.goal)))
            trigger.predicate?.let {
                add(AutomationDetail(getString(R.string.ua_debug_predicate), it.toFormattedJsonString()))
            }
        }
    }
}
