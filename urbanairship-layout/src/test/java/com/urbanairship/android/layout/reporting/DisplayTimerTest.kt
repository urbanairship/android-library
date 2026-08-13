/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.urbanairship.TestClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class DisplayTimerTest {

    private val clock = TestClock()
    private val lifecycleOwner = TestLifecycleOwner()

    private fun timer(restored: Duration = Duration.ZERO): DisplayTimer =
        DisplayTimer(lifecycleOwner, restored, clock)

    @Test
    public fun testStartsAtZero() {
        assertEquals(Duration.ZERO, timer().time)
    }

    @Test
    public fun testRestoredTimeIsTheStartingValue() {
        assertEquals(30.seconds, timer(restored = 30.seconds).time)
    }

    @Test
    public fun testNegativeRestoredTimeIsIgnored() {
        assertEquals(Duration.ZERO, timer(restored = (-5).seconds).time)
    }

    @Test
    public fun testTimeDoesNotAccrueWhilePaused() {
        val timer = timer()

        clock.advanceBy(10.seconds)

        assertEquals(Duration.ZERO, timer.time)
    }

    @Test
    public fun testTimeAccruesWhileResumed() {
        val timer = timer()
        timer.onResume()

        clock.advanceBy(10.seconds)

        assertEquals(10.seconds, timer.time)
    }

    @Test
    public fun testTimeStopsAccruingAfterPause() {
        val timer = timer()

        timer.onResume()
        clock.advanceBy(10.seconds)
        timer.onPause()

        clock.advanceBy(1.seconds)

        assertEquals(10.seconds, timer.time)
    }

    @Test
    public fun testTimeAccumulatesAcrossResumePauseCycles() {
        val timer = timer()

        timer.onResume()
        clock.advanceBy(5.seconds)
        timer.onPause()

        // Time spent in the background is not counted.
        clock.advanceBy(1.minutes)

        timer.onResume()
        clock.advanceBy(3.seconds)
        timer.onPause()

        assertEquals(8.seconds, timer.time)
    }

    @Test
    public fun testRestoredTimeAccumulatesWithNewTime() {
        val timer = timer(restored = 30.seconds)

        timer.onResume()
        clock.advanceBy(10.seconds)

        assertEquals(40.seconds, timer.time)
    }

    /**
     * An `onPause` with no preceding `onResume` must be a no-op. The previous implementation
     * defaulted the resume time to 0, so this added the entire epoch-to-now span.
     */
    @Test
    public fun testUnpairedPauseIsIgnored() {
        val timer = timer()

        timer.onPause()

        assertEquals(Duration.ZERO, timer.time)
    }

    @Test
    public fun testRepeatedResumeUsesTheLatestResumeTime() {
        val timer = timer()

        timer.onResume()
        clock.advanceBy(5.seconds)
        timer.onResume()
        clock.advanceBy(2.seconds)

        assertEquals(2.seconds, timer.time)
    }

    @Test
    public fun testSubSecondPrecision() {
        val timer = timer()

        timer.onResume()
        clock.advanceBy(1500.milliseconds)

        assertEquals(1500.milliseconds, timer.time)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry
    }
}
