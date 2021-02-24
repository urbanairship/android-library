package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.actions.Actions
import com.urbanairship.debug.utils.PendingResultLiveData

class ActionsScheduleViewModel : ViewModel() {

    val schedules: LiveData<List<Schedule<Actions>>>

    init {
        val pendingResultLiveData = PendingResultLiveData(InAppAutomation.shared().actionSchedules)
        schedules = Transformations.map(pendingResultLiveData) { collection ->
            collection.toList()
        }
    }
}
