/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.urbanairship.json.JsonMap

class ExtrasDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_EXTRAS = "extras"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        return requireArguments().parseJson(ARGUMENT_EXTRAS) {
            MutableLiveData(extrasDetails(it.optMap()))
        }
    }

    private fun extrasDetails(extras: JsonMap): List<AutomationDetail> =
            extras.map { (key, value) -> AutomationDetail(key, value.toString()) }
}
