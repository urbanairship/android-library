/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import com.urbanairship.annotation.OpenForTesting
import java.time.Duration as JavaDuration
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
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
            val start = clock.now()
            var remaining = remaining(start, duration)

            // We've had issues with really long delays not firing at the right period of time.
            // This works around those issues by breaking long sleeps into chunks.
            while (remaining > Duration.ZERO) {
                onSleep(remaining.coerceAtMost(MAX_DELAY_INTERVAL))
                remaining = remaining(start, duration)
            }
        }
    }

    protected suspend fun onSleep(duration: Duration) {
        delay(duration)
    }

    private fun remaining(start: Instant, duration: Duration): Duration {
        return duration - JavaDuration.between(start, clock.now()).toKotlinDuration()
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public val default: TaskSleeper = TaskSleeper(Clock.DEFAULT_CLOCK)

        private val MAX_DELAY_INTERVAL = 30.seconds
    }
}
