/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class BannerAutoDismissTimerTest {

    @Test
    public fun testFullDurationDismisses(): TestResult = runTest {
        val timer = BannerAutoDismissTimer(
            duration = 1000.milliseconds,
            elapsedTime = { testScheduler.currentTime.milliseconds }
        )
        var timedOut = false

        val job = launch { timer.start { timedOut = true } }

        advanceTimeBy(999L)
        runCurrent()
        assertFalse(timedOut)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(timedOut)
        assertEquals(0.milliseconds, timer.remaining)

        job.join()
    }

    @Test
    public fun testPauseStopsCountdown(): TestResult = runTest {
        val timer = BannerAutoDismissTimer(
            duration = 1000.milliseconds,
            elapsedTime = { testScheduler.currentTime.milliseconds }
        )
        var timedOut = false

        val job = launch { timer.start { timedOut = true } }
        advanceTimeBy(400L)
        runCurrent()

        // Pausing (cancelling the countdown) banks the elapsed time.
        job.cancelAndJoin()
        assertFalse(timedOut)
        assertEquals(600.milliseconds, timer.remaining)

        // Time passing while paused doesn't tick the countdown.
        advanceTimeBy(10_000L)
        runCurrent()
        assertFalse(timedOut)
        assertEquals(600.milliseconds, timer.remaining)
    }

    @Test
    public fun testResumeContinuesFromRemaining(): TestResult = runTest {
        val timer = BannerAutoDismissTimer(
            duration = 1000.milliseconds,
            elapsedTime = { testScheduler.currentTime.milliseconds }
        )
        var timedOut = false

        val first = launch { timer.start { timedOut = true } }
        advanceTimeBy(400L)
        runCurrent()
        first.cancelAndJoin()
        assertEquals(600.milliseconds, timer.remaining)

        // Resuming continues the countdown from the remaining duration, not the full duration.
        val second = launch { timer.start { timedOut = true } }
        advanceTimeBy(599L)
        runCurrent()
        assertFalse(timedOut)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(timedOut)

        second.join()
    }

    @Test
    public fun testZeroRemainingDismissesImmediatelyOnResume(): TestResult = runTest {
        // A manual clock that can run ahead of the suspended delay, so that the full duration
        // can elapse while the timer is paused.
        var now = Duration.ZERO
        val timer = BannerAutoDismissTimer(duration = 1000.milliseconds, elapsedTime = { now })
        var timedOut = false

        val first = launch { timer.start { timedOut = true } }
        runCurrent()
        now = 1500.milliseconds
        first.cancelAndJoin()

        assertFalse(timedOut)
        assertEquals(0.milliseconds, timer.remaining)

        // Resuming with no time remaining dismisses immediately, without delaying.
        val second = launch { timer.start { timedOut = true } }
        runCurrent()
        assertTrue(timedOut)

        second.join()
    }
}
