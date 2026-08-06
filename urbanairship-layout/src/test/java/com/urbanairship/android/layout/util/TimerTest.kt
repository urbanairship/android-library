/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
public class TimerTest {

    private var fireCount = 0

    private fun timer(durationMs: Long): Timer = object : Timer(durationMs) {
        override fun onFinish() {
            fireCount++
        }
    }

    private fun idleFor(durationMs: Long) {
        ShadowLooper.shadowMainLooper().idleFor(durationMs, TimeUnit.MILLISECONDS)
    }

    private fun flush() {
        ShadowLooper.shadowMainLooper().idle()
    }

    @Test
    public fun testFiresOnceAfterDuration() {
        val timer = timer(1000)
        timer.start()
        assertTrue(timer.isStarted)

        idleFor(1000)

        assertEquals(1, fireCount)
        assertFalse(timer.isStarted)
    }

    /**
     * Regression test for the ADVSEG-10281 double-advance bug.
     *
     * Once a timer has fired, its remaining time is zero. Before the fix, a
     * subsequent [Timer.start] (e.g. from the pager's "media ready" restart or
     * `resumeStory()`) would post the trigger immediately and re-run [onFinish],
     * re-firing the previous page's `pager_next` and skipping a page. A fired
     * timer must be terminal.
     */
    @Test
    public fun testRestartAfterFireIsIgnored() {
        val timer = timer(1000)
        timer.start()
        idleFor(1000)
        assertEquals(1, fireCount)

        // Simulate an external restart after the timer has already fired.
        timer.start()
        idleFor(1000)

        assertEquals("A fired timer must not re-fire on restart", 1, fireCount)
        assertFalse(timer.isStarted)
    }

    /**
     * A timer stopped (paused) before firing must still resume and fire once,
     * so the terminal-fire guard does not regress media-loading / story-pause
     * behavior.
     */
    @Test
    public fun testStopBeforeFireStillResumesAndFires() {
        val timer = timer(1000)
        timer.start()

        idleFor(400)
        timer.stop()
        assertEquals(0, fireCount)

        // Resume: only the remaining time should be left, then it fires once.
        timer.start()
        idleFor(600)

        assertEquals(1, fireCount)
        assertFalse(timer.isStarted)
    }

    /**
     * A zero-duration timer fires immediately on start (via the
     * `remainingTimeMs <= 0` post path — the same path that caused the bug) and
     * is then terminal: restarting it must not fire again.
     */
    @Test
    public fun testZeroDurationFiresOnceThenIsTerminal() {
        val timer = timer(0)
        timer.start()
        flush()
        assertEquals(1, fireCount)

        timer.start()
        flush()
        assertEquals(1, fireCount)
    }
}
