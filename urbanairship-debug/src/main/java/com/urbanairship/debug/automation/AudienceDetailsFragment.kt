package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.urbanairship.automation.Audience
import com.urbanairship.automation.Schedule
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString

class AudienceDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val AUDIENCE_SCHEDULE = "schedule"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        var schedule = requireArguments().getParcelable<Schedule>(AUDIENCE_SCHEDULE)!!

        return MutableLiveData(audienceDetails(schedule.audience!!))
    }

    private fun audienceDetails(audience: Audience): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {

            audience.notificationsOptIn?.let {
                if (it) {
                    add(AutomationDetail(getString(R.string.ua_debug_audience_notifications_optin), getString(R.string.ua_opted_in)))
                } else {
                    add(AutomationDetail(getString(R.string.ua_debug_audience_notifications_optin), getString(R.string.ua_opted_out)))
                }
            }

            audience.locationOptIn?.let {
                if (it) {
                    add(AutomationDetail(getString(R.string.ua_debug_audience_location_optin), getString(R.string.ua_opted_in)))
                } else {
                    add(AutomationDetail(getString(R.string.ua_debug_audience_location_optin), getString(R.string.ua_opted_out)))
                }
            }

            add(AutomationDetail(getString(R.string.ua_debug_audience_language_tags), audience.languageTags.joinToString(", ")))

            audience.tagSelector?.let {
                add(AutomationDetail(getString(R.string.ua_debug_audience_tag_selector), it.toFormattedJsonString()))
            }

            audience.versionPredicate?.let {
                add(AutomationDetail(getString(R.string.ua_debug_audience_version_predicate), it.toFormattedJsonString()))
            }

            add(AutomationDetail(getString(R.string.ua_debug_audience_miss_behavior), audience.missBehavior))

            add(AutomationDetail(getString(R.string.ua_debug_audience_test_devices), audience.testDevices.joinToString(", ")))

            audience.newUser?.let {
                add(AutomationDetail(getString(R.string.ua_debug_audience_new_user), it.toString()))
            }
        }
    }
}
