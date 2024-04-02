package com.urbanairship.automation.rewrite.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
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
        clock.currentTimeMillis = 0
    }

    @After
    public fun tearDown() {
        subject.stopListening()
    }

    @Test
    public fun testManualStartStopWorks() {
        createSubject()
        subject.start()
        clock.currentTimeMillis = 2

        assertEquals(2, subject.time)

        clock.currentTimeMillis = 3
        subject.stop()

        assertEquals(3, subject.time)
    }

    @Test
    public fun testMultipleSessions() {
        createSubject()
        subject.start()
        clock.currentTimeMillis = 1
        assertEquals(1, subject.time)
        subject.stop()

        clock.currentTimeMillis += 1
        assertEquals(1, subject.time)
        subject.start()
        clock.currentTimeMillis += 2
        subject.stop()
        assertEquals(3, subject.time)

        clock.currentTimeMillis += 1
        assertEquals(3, subject.time)
    }

    @Test
    public fun testStartDoesntWorkIfAppInBackground() {
        createSubject(isForeground = false)
        subject.start()
        clock.currentTimeMillis = 2

        assertEquals(0, subject.time)
    }

    @Test
    public fun testDoubleStartDoesntRestCounter() {
        createSubject()

        subject.start()
        clock.currentTimeMillis = 2
        assertEquals(2, subject.time)
        clock.currentTimeMillis = 3
        subject.start()
        clock.currentTimeMillis = 2
        subject.stop()
        assertEquals(2, subject.time)
    }

    @Test
    public fun testDoubleStopDoesntDoubleCounter() {
        createSubject()
        subject.start()
        clock.currentTimeMillis = 3
        subject.stop()

        assertEquals(3, subject.time)

        clock.currentTimeMillis = 5
        subject.stop()

        assertEquals(3, subject.time)
    }

    @Test
    public fun testHandlingAppState() {
        createSubject(isForeground = false)

        subject.start()
        clock.currentTimeMillis = 3
        assertEquals(0, subject.time)
        stateTracker.foreground()
        clock.currentTimeMillis += 3
        assertEquals(3, subject.time)

        stateTracker.background()
        clock.currentTimeMillis = 5
        assertEquals(3, subject.time)
    }

    @Test
    public fun testActiveNotificationDoesNothingOnDisabledTimer() {
        createSubject(isForeground = false)
        assertEquals(0, subject.time)

        stateTracker.foreground()
        clock.currentTimeMillis += 3
        assertEquals(0, subject.time)

    }

    @Test
    public fun testTimerStopsOnEnteringBackground() {
        createSubject()
        subject.start()
        clock.currentTimeMillis = 2
        assertEquals(2, subject.time)

        stateTracker.background()
        clock.currentTimeMillis = 5
        assertEquals(2, subject.time)

        subject.stop()
        assertEquals(2, subject.time)
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
