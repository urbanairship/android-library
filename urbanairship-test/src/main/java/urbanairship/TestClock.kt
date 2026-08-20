/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.SystemClock
import com.urbanairship.util.Clock
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

public class TestClock public constructor() : Clock() {

    public var currentTime: Instant = Instant.ofEpochMilli(System.currentTimeMillis())
    public var elapsedRealtime: Duration = SystemClock.elapsedRealtime().milliseconds

    override fun now(): Instant {
        return currentTime
    }

    override fun elapsedRealtime(): Duration {
        return elapsedRealtime
    }

    /** Advances both the wall clock and the elapsed-realtime clock by [duration]. */
    public fun advanceBy(duration: Duration) {
        currentTime += duration.toJavaDuration()
        elapsedRealtime += duration
    }
}
