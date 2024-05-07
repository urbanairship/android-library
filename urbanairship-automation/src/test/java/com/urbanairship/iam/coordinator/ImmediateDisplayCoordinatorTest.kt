package com.urbanairship.iam.coordinator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import app.cash.turbine.test
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
public class ImmediateDisplayCoordinatorTest {
    private val activityMonitor = TestActivityMonitor()
    private val coordinator = ImmediateDisplayCoordinator(activityMonitor)

    @Test
    public fun testIsReady(): TestResult = runTest {
        coordinator.isReady.test {
            assertFalse(awaitItem())
            activityMonitor.foreground()
            assertTrue(awaitItem())

            coordinator.messageWillDisplay(mock())
            coordinator.messageFinishedDisplaying(mock())

            ensureAllEventsConsumed()

            activityMonitor.background()
            assertFalse(awaitItem())
        }
    }

    @Test
    public fun testStateFlow(): TestResult = runTest {
        assertFalse(coordinator.isReady.value)
        activityMonitor.foreground()
        assertTrue(coordinator.isReady.value)
        activityMonitor.background()
        assertFalse(coordinator.isReady.value)

        activityMonitor.foreground()
        assertTrue(coordinator.isReady.value)

        coordinator.messageWillDisplay(mockk())
        assertTrue(coordinator.isReady.value)
    }
}
