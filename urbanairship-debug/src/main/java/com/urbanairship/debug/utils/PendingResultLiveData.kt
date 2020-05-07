package com.urbanairship.debug.utils

import androidx.lifecycle.LiveData
import com.urbanairship.PendingResult

class PendingResultLiveData<T>(private val pendingResult: PendingResult<T>) : LiveData<T>() {
    override fun onActive() {
        super.onActive()
        pendingResult.addResultCallback {
            this.value = it
        }
    }
}
