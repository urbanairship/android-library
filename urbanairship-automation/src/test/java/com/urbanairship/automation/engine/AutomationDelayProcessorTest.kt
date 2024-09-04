package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.AutomationAppState
import com.urbanairship.automation.AutomationDelay
import com.urbanairship.automation.ExecutionWindow
import com.urbanairship.automation.ExecutionWindowProcessor
import com.urbanairship.automation.Rule
import com.urbanairship.util.TaskSleeper
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AutomationDelayProcessorTest {
    private val foregroundState = MutableStateFlow(false)

    private val activityMonitor: ActivityMonitor = mockk() {
        every { this@mockk.foregroundState } returns this@AutomationDelayProcessorTest.foregroundState
    }

    private val screenState = MutableStateFlow<String?>(null)
    private val regionState = MutableStateFlow<Set<String>>(emptySet())

    private val analytics: Analytics = mockk() {
        every { this@mockk.screenState } returns this@AutomationDelayProcessorTest.screenState
        every { this@mockk.regionState } returns this@AutomationDelayProcessorTest.regionState
    }

    private val testDispatcher = StandardTestDispatcher()
    private val clock = TestClock().apply {
        currentTimeMillis = 0
    }

    private val sleeper: TaskSleeper = spyk(
        TestTaskSleeper(clock) { sleep ->
            clock.currentTimeMillis += sleep.inWholeMilliseconds
        }
    )

    private val executionWindowProcessor: ExecutionWindowProcessor = mockk()

    private val processor = AutomationDelayProcessor(
        analytics = analytics,
        activityMonitor = activityMonitor,
        executionWindowProcessor = executionWindowProcessor,
        clock = clock,
        sleeper = sleeper
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testWaitConditions(): TestResult = runTest {
        val delay = AutomationDelay(
            seconds = 100,
            screens = listOf("screen1", "screen2"),
            regionId = "region1",
            appState = AutomationAppState.FOREGROUND
        )

        startProcessing(delay, this).test {
            assertFalse(awaitItem())
            screenState.value = "screen1"
            expectNoEvents()
            regionState.value = setOf("region1")
            expectNoEvents()
            foregroundState.value = true
            assertTrue(awaitItem())
        }

        coVerify {
            sleeper.sleep(100.seconds)
        }
    }

    @Test
    public fun testProcess(): TestResult = runTest {
        val executionWindow = ExecutionWindow(
            includes = listOf(Rule.Weekly(daysOfWeek = listOf(1)))
        )

        val delay = AutomationDelay(
            seconds = 100,
            screens = listOf("screen1", "screen2"),
            regionId = "region1",
            appState = AutomationAppState.FOREGROUND,
            executionWindow = executionWindow
        )

        coEvery { executionWindowProcessor.isActive(any()) } returns true

        startProcessing(delay, this).test {
            assertFalse(awaitItem())
            screenState.value = "screen1"
            expectNoEvents()
            regionState.value = setOf("region1")
            expectNoEvents()
            foregroundState.value = true
            assertTrue(awaitItem())
        }

        coVerify {
            sleeper.sleep(100.seconds)
        }

        coVerify { executionWindowProcessor.isActive(executionWindow) }
    }

    @Test
    public fun testTaskSleep(): TestResult = runTest {
        val delay = AutomationDelay(seconds = 100L)

        startProcessing(delay, this).test {
            assertFalse(awaitItem())
            assertTrue(awaitItem())
        }

        coVerify {
            sleeper.sleep(100.seconds)
        }
    }

    @Test
    public fun testRemainingSleep(): TestResult = runTest {
        val delay = AutomationDelay(seconds = 100L)

        clock.currentTimeMillis = 100 * 1000

        startProcessing(delay, this, triggerTime = 50 * 1000).test {
            assertFalse(awaitItem())
            assertTrue(awaitItem())
        }

        coVerify {
            sleeper.sleep(50.seconds)
        }
    }

    @Test
    public fun testSkipSleep(): TestResult = runTest {
        val delay = AutomationDelay(seconds = 100L)

        clock.currentTimeMillis = 100 * 1000

        startProcessing(delay, this, triggerTime = 0).test {
            assertFalse(awaitItem())
            assertTrue(awaitItem())
        }

        coVerify(exactly = 0) { sleeper.sleep(any()) }
    }

    @Test
    public fun testEmptyDelay(): TestResult = runTest {
        val delay = AutomationDelay()

        startProcessing(delay, this).test {
            assertFalse(awaitItem())
            assertTrue(awaitItem())
        }

        coVerify(exactly = 0) { sleeper.sleep(any()) }
        assertTrue(processor.areConditionsMet(delay))
    }

    @Test
    public fun testNilDelay(): TestResult = runTest {
        startProcessing(null, this).test {
            assertFalse(awaitItem())
            assertTrue(awaitItem())
        }
        coVerify(exactly = 0) { sleeper.sleep(any()) }
        assertTrue(processor.areConditionsMet(null))
    }

    @Test
    public fun testScreenConditions(): TestResult = runTest {
        val delay = AutomationDelay(screens = listOf("screen1", "screen2"))

        assertFalse(processor.areConditionsMet(delay))

        screenState.value = "screen1"
        assertTrue(processor.areConditionsMet(delay))

        screenState.value = "screen3"
        assertFalse(processor.areConditionsMet(delay))
    }

    @Test
    public fun testRegionCondition(): TestResult = runTest {
        val delay = AutomationDelay(regionId = "foo")
        assertFalse(processor.areConditionsMet(delay))

        regionState.value = setOf("foo")
        assertTrue(processor.areConditionsMet(delay))

        regionState.value = setOf("bar")
        assertFalse(processor.areConditionsMet(delay))
    }

    @Test
    public fun testForegroundAppState(): TestResult = runTest {
        val delay = AutomationDelay(appState = AutomationAppState.FOREGROUND)

        foregroundState.value = false
        assertFalse(processor.areConditionsMet(delay))

        foregroundState.value = true
        assertTrue(processor.areConditionsMet(delay))
    }

    @Test
    public fun testBackgroundAppState(): TestResult = runTest {
        val delay = AutomationDelay(appState = AutomationAppState.BACKGROUND)

        foregroundState.value = false
        assertTrue(processor.areConditionsMet(delay))

        foregroundState.value = true
        assertFalse(processor.areConditionsMet(delay))
    }

    @Test
    public fun testCancellation(): TestResult = runTest {
        val delay = AutomationDelay(
            seconds = 100,
            screens = listOf("screen1", "screen2"),
            regionId = "region1",
            appState = AutomationAppState.FOREGROUND
        )

        val processor = AutomationDelayProcessor(
            analytics = analytics,
            activityMonitor = activityMonitor,
            executionWindowProcessor = executionWindowProcessor,
            clock = clock,
            sleeper = TaskSleeper.default
        )

        val job = async {
            processor.process(delay, 0)
        }

        job.cancel()

        assertFalse(job.isCompleted)
        assertTrue(job.isCancelled)
    }

    @Test
    public fun testNotCancelled(): TestResult = runTest {
        val delay = AutomationDelay()

        val processor = AutomationDelayProcessor(
            analytics = analytics,
            activityMonitor = activityMonitor,
            executionWindowProcessor = executionWindowProcessor,
            clock = clock,
            sleeper = TaskSleeper.default
        )

        val job = async {
            processor.process(delay, 0)
        }

        job.join()

        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    private fun startProcessing(
        delay: AutomationDelay?,
        scope: TestScope,
        triggerTime: Long = 0
    ): StateFlow<Boolean> {
        val flow = MutableStateFlow(false)
        scope.launch(Dispatchers.IO) {
            processor.process(delay, triggerDate = triggerTime)
            flow.value = true
        }
        return flow
    }
}
