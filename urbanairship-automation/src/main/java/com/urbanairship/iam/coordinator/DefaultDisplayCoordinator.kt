/* Copyright Airship and Contributors */

package com.urbanairship.iam.coordinator

import com.urbanairship.AirshipDispatchers
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.util.TaskSleeper
import com.urbanairship.util.combineStates
import com.urbanairship.iam.InAppMessage
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DefaultDisplayCoordinator(
    displayInterval: Duration,
    activityMonitor: ActivityMonitor,
    private val sleeper: TaskSleeper = TaskSleeper.default,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO,
) : DisplayCoordinator {

    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    private var lockState: MutableStateFlow<LockState> = MutableStateFlow(LockState.UNLOCKED)
    private var unlockJob: Job? = null
    private val activeDisplayIds = mutableSetOf<String>()
    private val reservedImmediateIds = mutableSetOf<String>()
    private val _hasActiveDisplays = MutableStateFlow(false)
    val hasActiveDisplays: StateFlow<Boolean> = _hasActiveDisplays

    override val isReady: StateFlow<Boolean> = combineStates(
        lockState,
        activityMonitor.foregroundState
    ) { lockState, foregroundState ->
        lockState == LockState.UNLOCKED && foregroundState
    }

    private enum class LockState {
        UNLOCKED, LOCKED, UNLOCKING;
    }

    var displayInterval: Duration = displayInterval
        set(value) {
            field = value
            startUnlocking()
        }


    override fun messageWillDisplay(message: InAppMessage, scheduleId: String) {
        unlockJob?.cancel()
        reservedImmediateIds.remove(scheduleId)
        activeDisplayIds.add(scheduleId)
        _hasActiveDisplays.value = activeDisplayIds.isNotEmpty()
        lockState.value = LockState.LOCKED
    }

    override fun messageFinishedDisplaying(message: InAppMessage, scheduleId: String) {
        activeDisplayIds.remove(scheduleId)
        _hasActiveDisplays.value = activeDisplayIds.isNotEmpty()
        if (activeDisplayIds.isEmpty()) {
            startUnlocking()
        }
    }

    fun reserveImmediateDisplay(scheduleId: String) {
        unlockJob?.cancel()
        reservedImmediateIds.add(scheduleId)
        lockState.value = LockState.LOCKED
    }

    fun releaseImmediateDisplayReservation(scheduleId: String) {
        if (!reservedImmediateIds.remove(scheduleId)) {
            return
        }

        if (activeDisplayIds.isEmpty() && reservedImmediateIds.isEmpty()) {
            unlockJob?.cancel()
            lockState.value = LockState.UNLOCKED
        }
    }

    private fun startUnlocking() {
        unlockJob?.cancel()

        if (activeDisplayIds.isNotEmpty() || reservedImmediateIds.isNotEmpty()) {
            lockState.value = LockState.LOCKED
            return
        }

        lockState.update {
            if (it == LockState.UNLOCKED) {
                return@update it
            }

            LockState.UNLOCKING
        }

        if (lockState.value == LockState.UNLOCKING) {
            unlockJob = scope.launch {
                sleeper.sleep(displayInterval)
                if (isActive && activeDisplayIds.isEmpty()) {
                    lockState.value = LockState.UNLOCKED
                }
            }
        }
    }
}
