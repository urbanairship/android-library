/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.ScheduleData
import com.urbanairship.debug.utils.PendingResultLiveData

@Suppress("UNCHECKED_CAST")
class ScheduleListViewModel : ViewModel() {

    val schedules: LiveData<List<Schedule<out ScheduleData>>>

    init {
        val pendingResultLiveData = PendingResultLiveData(InAppAutomation.shared().schedules)
        schedules = Transformations.map(pendingResultLiveData) { collection ->
            collection.toList()
        }
    }
}
