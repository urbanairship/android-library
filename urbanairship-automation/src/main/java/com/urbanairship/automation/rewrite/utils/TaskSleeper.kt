package com.urbanairship.automation.rewrite.utils

import androidx.annotation.RestrictTo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TaskSleeper {
    internal suspend fun sleep(seconds: Long) {
        delay(TimeUnit.SECONDS.toMillis(seconds))
    }

    internal companion object {
        val default: TaskSleeper = TaskSleeper()
    }
}
