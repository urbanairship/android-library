package com.urbanairship.automation.rewrite.utils

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

internal class TaskSleeper {
    suspend fun sleep(seconds: Long) {
        delay(TimeUnit.SECONDS.toMillis(seconds))
    }

    companion object {
        val default: TaskSleeper = TaskSleeper()
    }
}
