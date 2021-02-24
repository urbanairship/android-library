package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.deferred.Deferred
import com.urbanairship.debug.utils.PendingResultLiveData

class DeferredScheduleViewModel : ViewModel() {

    val schedules: LiveData<List<Schedule<Deferred>>>

    init {
        val pendingResultLiveData = PendingResultLiveData(InAppAutomation.shared().deferredMessageSchedules)
        schedules = Transformations.map(pendingResultLiveData) { collection ->
            collection.toList()
        }
    }
}