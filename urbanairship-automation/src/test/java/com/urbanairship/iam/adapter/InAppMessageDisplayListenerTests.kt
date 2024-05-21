package com.urbanairship.iam.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.automation.utils.ActiveTimer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
    private var recordedEvents = mutableListOf<Pair<InAppEvent, LayoutData?>>()
    private val activityMonitor = TestActivityMonitor()
    private val clock = TestClock()
    private val timer = ActiveTimer(activityMonitor, clock)
    private var displayResult: DisplayResult? = null
    private val listener = InAppMessageDisplayListener(analytics, timer, { displayResult = it })

    @Before
    public fun setup() {
        every { analytics.recordEvent(any(), any()) } answers {
            recordedEvents.add(Pair(firstArg(), secondArg()))
        }

        coEvery { analytics.recordImpression() } just runs
    }

    @Test
    public fun testOnAppear() {
        assertFalse(timer.isStarted)
        listener.onAppear()

        verifyEvents(listOf(InAppDisplayEvent()))
        assertTrue(timer.isStarted)

        listener.onAppear()
        verifyEvents(listOf(InAppDisplayEvent()))
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
            InAppResolutionEvent.buttonTap(
                identifier = "button id",
                description = "button label",
                displayTime = 10
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
            InAppResolutionEvent.buttonTap(
                identifier = "button id",
                description = "button label",
                displayTime = 10
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
        verifyEvents(listOf(InAppResolutionEvent.timedOut(3)))
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
        verifyEvents(listOf(InAppResolutionEvent.userDismissed(3)))
        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    @Test
    public fun testOnMessageTapDismissed() {
        clock.currentTimeMillis = 0
        timer.start()
        clock.currentTimeMillis = 2

        listener.onMessageTapDismissed()

        verifyEvents(listOf(InAppResolutionEvent.messageTap(2)))
        assertFalse(timer.isStarted)
        assertEquals(displayResult, DisplayResult.FINISHED)
    }

    private fun verifyEvents(expected: List<InAppEvent>) {
        assertEquals(recordedEvents.size, expected.size)
        expected.forEachIndexed { index, event ->
            val recorded = recordedEvents[index].first
            assertEquals(event.name, recorded.name)
            assertEquals(event.data?.toJsonValue(), recorded.data?.toJsonValue())
        }
    }
}
