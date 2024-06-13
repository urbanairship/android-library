/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TaskSleeper {
    public suspend fun sleep(duration: Duration) {
        if (duration.isFinite() && duration.isPositive()) {
            delay(duration)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        public val default: TaskSleeper = TaskSleeper()
    }
}
