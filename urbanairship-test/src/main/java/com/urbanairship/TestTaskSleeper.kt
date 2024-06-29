package com.urbanairship

import com.urbanairship.util.TaskSleeper
import kotlin.time.Duration

class TestTaskSleeper(
    clock: TestClock,
    private val onSleep: ((Duration) -> Unit)? = null
): TaskSleeper(clock) {
    private val _sleeps: MutableList<Duration> = mutableListOf()
    val sleeps: List<Duration>
        get() = _sleeps.toList()

    override suspend fun onSleep(duration: Duration) {
        _sleeps += duration
        onSleep?.invoke(duration)
    }
}
