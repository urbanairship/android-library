package com.urbanairship.util.timer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActiveTimerTest {
    private val clock = TestClock()
    private val stateTracker = TestActivityMonitor()
    private lateinit var subject: ActiveTimer

    @Before
    public fun setup() {
        clock.currentTime = Instant.ofEpochMilli(0)
    }

    @After
    public fun tearDown() {
        subject.stopListening()
    }

    @Test
    public fun testManualStartStopWorks() {
        createSubject()
        subject.start()
        clock.currentTime = Instant.ofEpochMilli(2)

        assertEquals(2.milliseconds, subject.time)

        clock.currentTime = Instant.ofEpochMilli(3)
        subject.stop()

        assertEquals(3.milliseconds, subject.time)
    }

    @Test
    public fun testMultipleSessions() {
        createSubject()
        subject.start()
        clock.currentTime = Instant.ofEpochMilli(1)
        assertEquals(1.milliseconds, subject.time)
        subject.stop()

        clock.currentTime += (1).milliseconds
        assertEquals(1.milliseconds, subject.time)
        subject.start()
        clock.currentTime += (2).milliseconds
        subject.stop()
        assertEquals(3.milliseconds, subject.time)

        clock.currentTime += (1).milliseconds
        assertEquals(3.milliseconds, subject.time)
    }

    @Test
    public fun testStartDoesntWorkIfAppInBackground() {
        createSubject(isForeground = false)
        subject.start()
        clock.currentTime = Instant.ofEpochMilli(2)

        assertEquals(0.milliseconds, subject.time)
    }

    @Test
    public fun testDoubleStartDoesntRestCounter() {
        createSubject()

        subject.start()
        clock.currentTime = Instant.ofEpochMilli(2)
        assertEquals(2.milliseconds, subject.time)
        clock.currentTime = Instant.ofEpochMilli(3)
        subject.start()
        clock.currentTime = Instant.ofEpochMilli(2)
        subject.stop()
        assertEquals(2.milliseconds, subject.time)
    }

    @Test
    public fun testDoubleStopDoesntDoubleCounter() {
        createSubject()
        subject.start()
        clock.currentTime = Instant.ofEpochMilli(3)
        subject.stop()

        assertEquals(3.milliseconds, subject.time)

        clock.currentTime = Instant.ofEpochMilli(5)
        subject.stop()

        assertEquals(3.milliseconds, subject.time)
    }

    @Test
    public fun testHandlingAppState() {
        createSubject(isForeground = false)

        subject.start()
        clock.currentTime = Instant.ofEpochMilli(3)
        assertEquals(0.milliseconds, subject.time)
        stateTracker.foreground()
        clock.currentTime += (3).milliseconds
        assertEquals(3.milliseconds, subject.time)

        stateTracker.background()
        clock.currentTime = Instant.ofEpochMilli(5)
        assertEquals(3.milliseconds, subject.time)
    }

    @Test
    public fun testActiveNotificationDoesNothingOnDisabledTimer() {
        createSubject(isForeground = false)
        assertEquals(0.milliseconds, subject.time)

        stateTracker.foreground()
        clock.currentTime += (3).milliseconds
        assertEquals(0.milliseconds, subject.time)

    }

    @Test
    public fun testTimerStopsOnEnteringBackground() {
        createSubject()
        subject.start()
        clock.currentTime = Instant.ofEpochMilli(2)
        assertEquals(2.milliseconds, subject.time)

        stateTracker.background()
        clock.currentTime = Instant.ofEpochMilli(5)
        assertEquals(2.milliseconds, subject.time)

        subject.stop()
        assertEquals(2.milliseconds, subject.time)
    }

    private fun createSubject(isForeground: Boolean = true) {
        if (isForeground) {
            stateTracker.foreground()
        } else {
            stateTracker.background()
        }

        subject = ActiveTimer(stateTracker, clock)
    }
}
