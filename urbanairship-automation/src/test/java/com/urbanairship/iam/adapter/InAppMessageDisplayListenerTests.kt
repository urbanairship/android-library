package com.urbanairship.iam.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.android.layout.analytics.DisplayResult
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.events.InAppDisplayEvent
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.analytics.events.LayoutResolutionEvent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.util.timer.ManualTimer
import kotlin.time.Duration.Companion.milliseconds
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageDisplayListenerTests {
    private val analytics: InAppMessageAnalyticsInterface = mockk()
    private var recordedEvents = mutableListOf<Pair<LayoutEvent, LayoutData?>>()
    private val clock = TestClock()
    private val timer = ManualTimer(clock)
    private var displayResult: DisplayResult? = null
    private val listener = InAppMessageDisplayListener(analytics, timer) { displayResult = it }

    @Before
    public fun setup() {
        every { analytics.recordEvent(any(), any()) } answers {
            recordedEvents.add(Pair(firstArg(), secondArg()))
        }
    }

    @Test
    public fun testOnAppear() {
        assertFalse(timer.isStarted)
        listener.onAppear()

        verifyEvents(listOf(InAppDisplayEvent()))
        assertTrue(timer.isStarted)

        listener.onAppear()
        verifyEvents(listOf(InAppDisplayEvent(), InAppDisplayEvent()))
        assertNull(displayResult)
    }

    @Test
    public fun testOnButtonDismissed() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10

        assertTrue(timer.isStarted)

        val buttonInfo = InAppMessageButtonInfo(
            identifier = "button id",
            label = InAppMessageTextInfo(text = "button label"),
            behavior = InAppMessageButtonInfo.Behavior.DISMISS
        )

        listener.onButtonDismissed(buttonInfo)

        verifyEvents(listOf(
            LayoutResolutionEvent.buttonTap(
                identifier = "button id",
                description = "button label",
                displayTime = 10.milliseconds
            )
        ))

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testOnButtonCancel() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 10

        assertTrue(timer.isStarted)

        val buttonInfo = InAppMessageButtonInfo(
            identifier = "button id",
            label = InAppMessageTextInfo(text = "button label"),
            behavior = InAppMessageButtonInfo.Behavior.CANCEL
        )

        listener.onButtonDismissed(buttonInfo)

        verifyEvents(listOf(
            LayoutResolutionEvent.buttonTap(
                identifier = "button id",
                description = "button label",
                displayTime = 10.milliseconds
            )
        ))

        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.CANCEL)
    }

    @Test
    public fun testOnTimedOut() {
        clock.currentTimeMillis = 0
        timer.start()
        assertTrue(timer.isStarted)
        clock.currentTimeMillis = 3
        listener.onTimedOut()
        verifyEvents(listOf(LayoutResolutionEvent.timedOut(3.milliseconds)))
        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testOnUserDismissed() {
        clock.currentTimeMillis = 0
        timer.start()
        assertTrue(timer.isStarted)
        clock.currentTimeMillis = 3

        listener.onUserDismissed()
        verifyEvents(listOf(LayoutResolutionEvent.userDismissed(3.milliseconds)))
        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testOnMessageTapDismissed() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 2

        listener.onMessageTapDismissed()

        verifyEvents(listOf(LayoutResolutionEvent.messageTap(2.milliseconds)))
        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    private fun verifyEvents(expected: List<LayoutEvent>) {
        assertEquals(recordedEvents.size, expected.size)
        expected.forEachIndexed { index, event ->
            val recorded = recordedEvents[index].first
            assertEquals(event.eventType, recorded.eventType)
            assertEquals(event.data?.toJsonValue(), recorded.data?.toJsonValue())
        }
    }
}
