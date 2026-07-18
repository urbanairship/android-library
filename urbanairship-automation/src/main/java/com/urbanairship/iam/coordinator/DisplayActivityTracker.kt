/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DisplayActivityTracker {

    private val _activeCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val _isDisplaying: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isDisplaying: StateFlow<Boolean> = _isDisplaying.asStateFlow()

    fun messageWillDisplay() {
        updateCount(_activeCount.value + 1)
    }

    fun messageFinishedDisplaying() {
        updateCount(maxOf(0, _activeCount.value - 1))
    }

    private fun updateCount(count: Int) {
        _activeCount.value = count
        _isDisplaying.value = count > 0
    }
}
