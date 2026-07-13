package com.urbanairship.iam.coordinator

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisplayActivityTrackerTest {
    private val tracker = DisplayActivityTracker()

    @Test
    public fun testNotDisplayingByDefault() {
        assertFalse(tracker.isDisplaying.value)
    }

    @Test
    public fun testTracksDisplays() {
        tracker.messageWillDisplay()
        assertTrue(tracker.isDisplaying.value)

        tracker.messageWillDisplay()
        tracker.messageFinishedDisplaying()
        assertTrue(tracker.isDisplaying.value)

        tracker.messageFinishedDisplaying()
        assertFalse(tracker.isDisplaying.value)
    }

    @Test
    public fun testFinishWithoutDisplayDoesNotGoNegative() {
        tracker.messageFinishedDisplaying()
        assertFalse(tracker.isDisplaying.value)

        tracker.messageWillDisplay()
        assertTrue(tracker.isDisplaying.value)

        tracker.messageFinishedDisplaying()
        assertFalse(tracker.isDisplaying.value)
    }

    @Test
    public fun testUpdatesStreamChanges(): TestResult = runTest {
        tracker.activeCount.test {
            assertEquals(0, awaitItem())

            tracker.messageWillDisplay()
            assertEquals(1, awaitItem())

            tracker.messageWillDisplay()
            assertEquals(2, awaitItem())

            tracker.messageFinishedDisplaying()
            assertEquals(1, awaitItem())

            tracker.messageFinishedDisplaying()
            assertEquals(0, awaitItem())
        }
    }
}
