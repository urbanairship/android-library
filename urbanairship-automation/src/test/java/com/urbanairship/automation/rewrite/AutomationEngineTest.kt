package com.urbanairship.automation.rewrite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.rewrite.engine.AutomationDelayProcessor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock()
    private lateinit var engine: AutomationEngine

    @Before
    public fun setup() {
        engine = AutomationEngine(
            context = context,
            store = AutomationStore.createInMemoryDatabase(context),
            executor = mockk(relaxed = true),
            preparer = mockk(relaxed = true),
            scheduleConditionsChangedNotifier = ScheduleConditionsChangedNotifier(),
            eventsFeed = mockk(relaxed = true),
            triggerProcessor = mockk(relaxed = true),
            delayProcessor = mockk(relaxed = true),
            clock = clock,
            sleeper = TaskSleeper.default
        )
    }

    @Test
    public fun testStartStop(): TestResult = runTest {
        assertFalse(engine.isStarted())
        engine.start()
        assertTrue(engine.isStarted())
        engine.stop()
        assertFalse(engine.isStarted())
    }

    @Test
    public fun testSetEnginePaused(): TestResult = runTest {
        assertFalse(engine.isPaused())
        engine.setEnginePaused(true)
        assertTrue(engine.isPaused())
        engine.setEnginePaused(false)
        assertFalse(engine.isPaused())
    }

    @Test
    public fun testSetExecutionPaused(): TestResult = runTest {
        assertFalse(engine.isExecutionPaused())
        engine.setExecutionPaused(true)
        assertTrue(engine.isExecutionPaused())
        engine.setExecutionPaused(false)
        assertFalse(engine.isExecutionPaused())
    }

    @Test
    public fun testStopSchedules(): TestResult = runTest {
        engine.upsertSchedules(listOf(
            AutomationSchedule(
                identifier = "test",
                triggers = listOf(),
                data = AutomationSchedule.ScheduleData.InAppMessageData(
                    InAppMessage(
                        name = "test",
                        displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("test")))
                    )
                ),
                created = clock.currentTimeMillis.toULong()
            )
        ))

        assertNotNull(engine.getSchedule("test"))
        engine.stopSchedules(listOf("test"))
        assertNull(engine.getSchedule("test"))
    }

    @Test
    public fun testUpsertSchedules(): TestResult = runTest {
        assertNull(engine.getSchedule("test"))

        engine.upsertSchedules(listOf(
            AutomationSchedule(
                identifier = "test",
                triggers = listOf(),
                data = AutomationSchedule.ScheduleData.InAppMessageData(
                    InAppMessage(
                        name = "test",
                        displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("test")))
                    )
                ),
                created = clock.currentTimeMillis.toULong()
            )
        ))

        assertNotNull(engine.getSchedule("test"))
    }

    @Test
    public fun testCancelSchedule(): TestResult = runTest {
        engine.upsertSchedules(listOf(
            AutomationSchedule(
                identifier = "test",
                triggers = listOf(),
                data = AutomationSchedule.ScheduleData.InAppMessageData(
                    InAppMessage(
                        name = "test",
                        displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("test")))
                    )
                ),
                created = clock.currentTimeMillis.toULong()
            )
        ))

        assertNotNull(engine.getSchedule("test"))
        engine.cancelSchedules(listOf("test"))
        assertNull(engine.getSchedule("test"))
    }
}
