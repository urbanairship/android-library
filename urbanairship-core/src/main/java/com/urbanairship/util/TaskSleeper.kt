/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import com.urbanairship.annotation.OpenForTesting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * @hide
 */
@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TaskSleeper(
    private val clock: Clock,
) {
    public suspend fun sleep(duration: Duration) {
        if (duration.isFinite() && duration.isPositive()) {
            val start = clock.currentTimeMillis()
            var remaining = remainingMillis(start, duration)

            // We've had issues with really long delays not firing at the right period of time.
            // This works around those issues by breaking long sleeps into chunks.
            while (remaining > 0) {
                val interval = remaining.coerceAtMost(MAX_DELAY_INTERVAL)
                onSleep(interval.milliseconds)
                remaining = remainingMillis(start, duration)
            }
        }
    }

    protected suspend fun onSleep(duration: Duration) {
        delay(duration)
    }

    private fun remainingMillis(start: Long, duration: Duration): Long {
        return duration.inWholeMilliseconds - (clock.currentTimeMillis() - start)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        public val default: TaskSleeper = TaskSleeper(Clock.DEFAULT_CLOCK)

        private val MAX_DELAY_INTERVAL = 30.seconds.inWholeMilliseconds
    }
}
