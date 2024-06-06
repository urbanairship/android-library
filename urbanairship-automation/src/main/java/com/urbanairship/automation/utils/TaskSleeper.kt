/* Copyright Airship and Contributors */

package com.urbanairship.automation.utils

import kotlin.time.Duration
import kotlinx.coroutines.delay

internal class TaskSleeper {
    internal suspend fun sleep(duration: Duration) {
        if (duration.isFinite() && duration.isPositive()) {
            delay(duration)
        }
    }

    internal companion object {
        val default: TaskSleeper = TaskSleeper()
    }
}
