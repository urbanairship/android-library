package com.urbanairship.automation.rewrite.utils

import androidx.annotation.RestrictTo
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlinx.coroutines.delay

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TaskSleeper {
    internal suspend fun sleep(duration: Duration) {
        if (duration.isFinite() && duration.isPositive()) {
            delay(duration)
        }
    }

    internal companion object {
        val default: TaskSleeper = TaskSleeper()
    }
}
