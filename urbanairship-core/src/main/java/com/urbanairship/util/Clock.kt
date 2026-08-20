/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.os.SystemClock
import androidx.annotation.RestrictTo
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class Clock public constructor() {

    /**
     * The current wall-clock time, truncated to milliseconds.
     *
     * [Instant.now] resolves to sub-millisecond precision, but timestamps are persisted and
     * sent over the wire as epoch milliseconds. Truncating here keeps a value read back from
     * storage comparable to a freshly read one, instead of differing by a stray few hundred
     * microseconds.
     */
    public open fun now(): Instant {
        return Instant.ofEpochMilli(System.currentTimeMillis())
    }

    /**
     * Time elapsed since boot, including time spent in deep sleep.
     *
     * Unlike [now], this is monotonic and unaffected by wall-clock adjustments, making it
     * suitable for measuring intervals.
     */
    public open fun elapsedRealtime(): Duration {
        return SystemClock.elapsedRealtime().milliseconds
    }

    public companion object {
        public val DEFAULT_CLOCK: Clock = Clock()
    }
}
