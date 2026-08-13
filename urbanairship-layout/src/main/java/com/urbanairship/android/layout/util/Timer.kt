/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.os.Handler
import android.os.Looper
import com.urbanairship.util.Clock
import kotlin.time.Duration

/**
 * Timer that can be stopped and started.
 */
public abstract class Timer public constructor(
    private val duration: Duration,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {
    public var isStarted: Boolean = false
        private set
    private var startTime: Duration = Duration.ZERO
    private var remainingTime: Duration = duration
    private var elapsedTime: Duration = Duration.ZERO
    private var hasFired: Boolean = false
    private val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
    private val trigger = Runnable {
        if (isStarted) {
            hasFired = true
            stop()
            onFinish()
        }
    }

    /**
     * Starts the timer.
     *
     * Once the timer has fired (i.e. [onFinish] has run), it is terminal and
     * subsequent calls to [start] are ignored. This prevents an already-expired
     * timer (whose remaining time is zero) from re-firing immediately when a
     * restart is requested by an external caller.
     */
    public fun start() {
        if (isStarted || hasFired) {
            return
        }

        isStarted = true
        startTime = clock.elapsedRealtime()

        if (remainingTime > Duration.ZERO) {
            handler.postDelayed(trigger, remainingTime.inWholeMilliseconds)
        } else {
            handler.post(trigger)
        }
    }

    /**
     * Stops the timer.
     */
    public fun stop() {
        if (!isStarted) {
            return
        }

        val sinceStart = clock.elapsedRealtime() - startTime
        elapsedTime += sinceStart

        isStarted = false
        handler.removeCallbacks(trigger)
        remainingTime = (remainingTime - sinceStart).coerceAtLeast(Duration.ZERO)
    }

    /**
     * Gets the total run time.
     *
     * @return The total run time.
     */
    public fun getRunTime(): Duration {
        if (isStarted) {
            return elapsedTime + (clock.elapsedRealtime() - startTime)
        }

        return elapsedTime
    }

    /**
     * Gets the progress in percentage.
     *
     * @return The progress in percentage.
     */
    public fun getProgress(): Int {
        if (duration == Duration.ZERO) {
            return 0 // Return 0 progress if the duration is zero to prevent divide by zero
        }
        return (getRunTime().inWholeMilliseconds * 100 / duration.inWholeMilliseconds).toInt()
    }

    /**
     * Called when the timer finishes.
     */
    protected abstract fun onFinish()
}
