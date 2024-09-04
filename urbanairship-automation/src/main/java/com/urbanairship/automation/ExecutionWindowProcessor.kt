/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

internal class ExecutionWindowProcessor(
    context: Context,
    private val taskSleeper: TaskSleeper = TaskSleeper.default,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val onEvaluate: (ExecutionWindow, Date) -> ExecutionWindowResult = {
            window, date -> window.nextAvailability(date)
    },
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val tasksState = MutableStateFlow<Set<Job>>(emptySet())

    init {
        listenToTimeZoneChange(context)
    }

    private fun listenToTimeZoneChange(context: Context) {
        val receiver = TimeZoneReceiver.shared
        receiver.handler = {
            tasksState.update {
                it.forEach { task -> task.cancel() }
                emptySet()
            }
        }

        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}

        val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    private suspend fun sleep(duration: Duration) {
        val job = scope.launch {
            taskSleeper.sleep(duration)
        }

        tasksState.update { it + job }
        job.join()
        tasksState.update { it - job }
    }

    private fun nextAvailability(window: ExecutionWindow): ExecutionWindowResult {
        return try {
            onEvaluate(window, Date(clock.currentTimeMillis()))
        } catch (ex: Exception) {
            // We failed to process the window, use a long retry to prevent it from
            // busy waiting
            UALog.e(ex) { "Failed to process execution window" }
            ExecutionWindowResult.Retry(1.days)
        }
    }

    suspend fun process(window: ExecutionWindow) {
        while (true) {
            when(val result = nextAvailability(window)) {
                ExecutionWindowResult.Now -> break
                is ExecutionWindowResult.Retry -> {
                    sleep(result.delay)
                }
            }
        }
    }

    fun isActive(window: ExecutionWindow): Boolean  {
        return nextAvailability(window) == ExecutionWindowResult.Now
    }
}

internal class TimeZoneReceiver(
    var handler: (() -> Unit)? = null
): BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        handler?.invoke()
    }

    companion object {
        val shared = TimeZoneReceiver()
    }
}
