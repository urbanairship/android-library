package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import com.urbanairship.AirshipDispatchers
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.combineStates
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class DefaultDisplayCoordinator(
    displayInterval: Long,
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

    var displayInterval: Long = displayInterval
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
        lockState.update {
            if (it == LockState.UNLOCKED) {
                return@update it
            }

            unlockJob?.cancel()

            unlockJob = scope.launch {
                sleeper.sleep(displayInterval)
                yield()
                if (isActive) {
                    lockState.compareAndSet(LockState.UNLOCKING, LockState.UNLOCKED)
                }
            }

            LockState.UNLOCKING
        }
    }
}
