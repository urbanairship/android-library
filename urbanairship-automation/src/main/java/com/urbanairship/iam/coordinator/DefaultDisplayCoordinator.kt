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


    override fun messageWillDisplay(message: InAppMessage) {
        lockState.value = LockState.LOCKED
    }

    override fun messageFinishedDisplaying(message: InAppMessage) {
        startUnlocking()
    }

    private fun startUnlocking() {
        unlockJob?.cancel()

        lockState.update {
            if (it == LockState.UNLOCKED) {
                return@update it
            }

            LockState.UNLOCKING
        }

        if (lockState.value == LockState.UNLOCKING) {
            unlockJob = scope.launch {
                sleeper.sleep(displayInterval)
                if (isActive) {
                    lockState.value = LockState.UNLOCKED
                }
            }
        }
    }
}
