/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.urbanairship.debug.utils.PendingResultLiveData
import com.urbanairship.iam.InAppAutomation
import com.urbanairship.iam.InAppMessageSchedule

class ScheduleListViewModel : ViewModel() {

    val schedules: LiveData<List<InAppMessageSchedule>>

    init {
        val pendingResultLiveData = PendingResultLiveData<Collection<InAppMessageSchedule>>(InAppAutomation.shared().schedules)
        schedules = Transformations.map(pendingResultLiveData) { collection ->
            collection.toList()
        }
    }
}
