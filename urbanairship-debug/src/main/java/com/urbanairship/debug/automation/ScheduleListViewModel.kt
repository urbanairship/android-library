/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.debug.utils.PendingResultLiveData

class ScheduleListViewModel : ViewModel() {

    val schedules: LiveData<List<Schedule>>

    init {
        val pendingResultLiveData = PendingResultLiveData<Collection<Schedule>>(InAppAutomation.shared().schedules)
        schedules = Transformations.map(pendingResultLiveData) { collection ->
            collection.toList().filter {
                        it.type == Schedule.TYPE_IN_APP_MESSAGE
                    }
        }
    }
}
