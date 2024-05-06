package com.urbanairship.automation.rewrite.inappmessage.displaycoordinators

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DefaultDisplayCoordinator
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
public class DefaultDisplayCoordinatorTest {
    private val activityMonitor = TestActivityMonitor()
    private val sleeper: TaskSleeper = mockk(relaxed = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coordinator = DefaultDisplayCoordinator(
        displayInterval = 10.seconds,
        activityMonitor = activityMonitor,
        sleeper = sleeper,
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    public fun testIsReadyActivityMonitor(): TestResult = runTest {
        coordinator.isReady.test {
            assertFalse(awaitItem())
            activityMonitor.foreground()
            assertTrue(awaitItem())
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
        assertFalse(coordinator.isReady.value)
    }

    @Test
    public fun testIsReadyLocking(): TestResult = runTest {
        activityMonitor.foreground()

        coordinator.isReady.test {
            assertTrue(awaitItem())
            coordinator.messageWillDisplay(mock())
            assertFalse(awaitItem())

            coordinator.messageFinishedDisplaying(mock())
            assertTrue(awaitItem())
        }

        coVerify { sleeper.sleep(10.seconds) }
    }
}
