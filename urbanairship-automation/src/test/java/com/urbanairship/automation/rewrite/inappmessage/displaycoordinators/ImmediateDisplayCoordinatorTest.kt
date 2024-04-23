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
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.ImmediateDisplayCoordinator
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockkStatic
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
public class ImmediateDisplayCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activityMonitor = TestActivityMonitor()
    private val stateTracker: InAppActivityMonitor = InAppActivityMonitor(activityMonitor)
    private val coordinator = ImmediateDisplayCoordinator(stateTracker)

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
    public fun testWaitForReady(): TestResult = runTest {
        activityMonitor.background()

        val job = launch { coordinator.waitForReady() }
        yield()
        backgroundScope.launch {
            activityMonitor.startActivity()
        }

        job.join()
    }

    @Test
    public fun testDisplayMultiple() {
        activityMonitor.foreground()

        val foo = InAppMessage(
            name = "foo",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL))
        )

        val bar = InAppMessage(
            name = "bar",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL))
        )

        coordinator.messageWillDisplay(foo)
        assertTrue(coordinator.getIsReady())

        coordinator.messageWillDisplay(bar)
        assertTrue(coordinator.getIsReady())

        coordinator.messageFinishedDisplaying(foo)
        assertTrue(coordinator.getIsReady())

        coordinator.messageFinishedDisplaying(bar)
        assertTrue(coordinator.getIsReady())
    }
}
