package com.urbanairship.automation.rewrite.inappmessage.displaycoordinators

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.UAirship
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DefaultDisplayCoordinator
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import com.urbanairship.json.JsonValue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DefaultDisplayCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activityMonitor = TestActivityMonitor()
    private val stateTracker: InAppActivityMonitor = InAppActivityMonitor(activityMonitor)
    private val sleeper: TaskSleeper = mockk()
    private val coordinator = DefaultDisplayCoordinator(
        displayInterval = 10,
        activityMonitor = stateTracker,
        sleeper = sleeper
    )
    private val message = InAppMessage(
        name = "test message",
        displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("foo")))
    )

    @Before
    public fun setup() {
        mockkStatic(UAirship::class)
        every { UAirship.getApplicationContext() } returns context
        stateTracker.init()
    }

    @Test
    public fun testIsReady() {
        activityMonitor.foreground()
        assertTrue(coordinator.getIsReady())

        activityMonitor.background()
        assertFalse(coordinator.getIsReady())
    }

    @Test
    public fun testIsReadyLocking(): TestResult = runTest {

        var sleepTime: Long? = null
        coEvery { sleeper.sleep(any()) } answers {
            sleepTime = firstArg()
        }

        activityMonitor.foreground()
        assertTrue(coordinator.getIsReady())

        coordinator.messageWillDisplay(message)
        assertFalse(coordinator.getIsReady())

        coordinator.messageFinishedDisplaying(message)
        coordinator.waitForReady()
        assertTrue(coordinator.getIsReady())

        assertEquals(10L, sleepTime)
    }

    @Test
    public fun testWaitForReady(): TestResult = runTest {
        activityMonitor.background()

        val job = launch { coordinator.waitForReady() }
        yield()
        backgroundScope.launch {
            activityMonitor.startActivity()
        }

        job.join()
    }
}
