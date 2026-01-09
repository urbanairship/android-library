/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.max

/**
 * Timer that can be stopped and started.
 */
public abstract class Timer public constructor(
    private val duration: Long
) {
    public var isStarted: Boolean = false
        private set
    private var startTimeMs: Long = 0
    private var remainingTimeMs: Long = duration
    private var elapsedTimeMs: Long = 0
    private val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
    private val trigger = Runnable {
        if (isStarted) {
            stop()
            onFinish()
        }
    }

    /**
     * Starts the timer.
     */
    public fun start() {
        if (isStarted) {
            return
        }

        isStarted = true
        startTimeMs = SystemClock.elapsedRealtime()

        if (remainingTimeMs > 0) {
            handler.postDelayed(trigger, remainingTimeMs)
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

        elapsedTimeMs += SystemClock.elapsedRealtime() - startTimeMs

        isStarted = false
        handler.removeCallbacks(trigger)
        remainingTimeMs = max(0, remainingTimeMs - (SystemClock.elapsedRealtime() - startTimeMs))
    }

    /**
     * Gets the total run time in milliseconds.
     *
     * @return The total run time in milliseconds.
     */
    public fun getRunTime(): Long {
        if (isStarted) {
            return elapsedTimeMs + SystemClock.elapsedRealtime() - startTimeMs
        }

        return elapsedTimeMs
    }

    /**
     * Gets the progress in percentage.
     *
     * @return The progress in percentage.
     */
    public fun getProgress(): Int {
        if (duration == 0L) {
            return 0 // Return 0 progress if the duration is zero to prevent divide by zero
        }
        return (getRunTime() * 100 / duration).toInt()
    }

    /**
     * Called when the timer finishes.
     */
    protected abstract fun onFinish()
}
