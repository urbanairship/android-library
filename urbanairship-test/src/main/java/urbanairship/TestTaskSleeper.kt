package com.urbanairship

import com.urbanairship.util.TaskSleeper
import kotlin.time.Duration

public class TestTaskSleeper(
    clock: TestClock,
    private val onSleepBlock: (suspend (Duration) -> Unit)? = null
): TaskSleeper(clock) {
    private val _sleeps: MutableList<Duration> = mutableListOf()
    public val sleeps: List<Duration>
        get() = _sleeps.toList()

    override suspend fun sleep(duration: Duration) {
        _sleeps += duration
        onSleepBlock?.invoke(duration)
    }
}
