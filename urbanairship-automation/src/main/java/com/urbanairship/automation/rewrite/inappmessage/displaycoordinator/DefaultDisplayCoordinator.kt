package com.urbanairship.automation.rewrite.inappmessage.displaycoordinator

import com.urbanairship.AirshipDispatchers
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class DefaultDisplayCoordinator(
    displayInterval: Long,
    private val activityMonitor: InAppActivityMonitor,
    private val sleeper: TaskSleeper = TaskSleeper.default,
    private val scope: CoroutineScope = CoroutineScope(AirshipDispatchers.newSerialDispatcher() + SupervisorJob())
) : DisplayCoordinatorInterface {

    private enum class LockState {
        UNLOCKED, LOCKED, UNLOCKING;
    }

    private val lockState = ValueWithChannel(LockState.UNLOCKED)
    private var unlockJob: Job? = null

    var displayInterval: Long = displayInterval
        set(value) {
            field = value
            if (lockState.value == LockState.UNLOCKING) {
                unlockJob?.cancel()
                startUnlocking()
            }
        }

    override fun getIsReady(): Boolean {
        return lockState.value == LockState.UNLOCKED && activityMonitor.isAppForegrounded
    }

    override fun messageWillDisplay(message: InAppMessage) {
        lockState.value = LockState.LOCKED
    }

    override fun messageFinishedDisplaying(message: InAppMessage) {
        startUnlocking()
    }

    override suspend fun waitForReady() {
        while (!getIsReady()) {
            yield()

            if (!activityMonitor.isAppForegrounded) {
                activityMonitor.waitForActive()
                yield()
            }

            if (lockState.value == LockState.UNLOCKED) {
                continue
            }

            for (update in lockState.updates) {
                yield()
                if (update == LockState.UNLOCKED) {
                    break
                }
            }
        }
    }

    private fun startUnlocking() {
        if (lockState.value == LockState.UNLOCKED) {
            return
        }

        lockState.value = LockState.UNLOCKING

        unlockJob = scope.launch {
            sleeper.sleep(displayInterval)
            yield()
            if (isActive) {
                lockState.value = LockState.UNLOCKED
            }
        }
    }
}

internal class ValueWithChannel<T : Any>(
    initial: T
) {
    private var _value = initial
    private val channel = Channel<T>(Channel.CONFLATED)
    var updates: ReceiveChannel<T> = channel

    var value: T
        get() { synchronized(_value) { return _value } }
        set(value) {
            synchronized(_value) {
                _value = value
                channel.trySend(value)
            }
        }
}
