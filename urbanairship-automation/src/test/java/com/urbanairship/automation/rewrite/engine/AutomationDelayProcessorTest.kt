package com.urbanairship.automation.rewrite.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.automation.rewrite.AutomationAppState
import com.urbanairship.automation.rewrite.AutomationDelay
import com.urbanairship.automation.rewrite.utils.TaskSleeper
import com.urbanairship.locale.LocaleManager
import kotlin.time.Duration
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationDelayProcessorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock()
    private val activityMonitor = TestActivityMonitor()
    private val sleeper: TaskSleeper = mockk()
    private lateinit var analytics: Analytics
    private lateinit var processor: AutomationDelayProcessor
    private val sleepIntervals = mutableListOf<Long>()

    @Before
    public fun setup() {
        val dataStore = PreferenceDataStore.inMemoryStore(context)
        analytics = Analytics(
            context,
            dataStore,
            TestAirshipRuntimeConfig(),
            PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL),
            mockk(),
            LocaleManager(context, dataStore),
            mockk()
        )

        processor = AutomationDelayProcessor(
            analytics = analytics,
            appStateTracker = activityMonitor,
            clock = clock,
            sleeper = sleeper
        )

        clock.currentTimeMillis = 0
        coEvery { sleeper.sleep(any()) } coAnswers {
            sleepIntervals.add(firstArg())
        }
    }

    @Test
    public fun testWaitConditions(): TestResult = runTest {
        val delay = AutomationDelay(
            seconds = 100,
            screens = listOf("screen1", "screen2"),
            regionID = "region1",
            appState = AutomationAppState.FOREGROUND
        )

        val job = startProcessing(delay, this)

        assertTrue(job.isActive)
        assertFalse(job.isCompleted)

        analytics.trackScreen("screen1")
        trackRegionEnter("region1")
        activityMonitor.foreground()

        job.await()
        assertEquals(listOf(100L), sleepIntervals)
    }

    @Test
    public fun testTaskSleep(): TestResult = runTest {
        val delay = AutomationDelay(seconds = 100L)

        val job = startProcessing(delay, this)

        job.await()
        assertEquals(listOf(100L), sleepIntervals)
    }

    @Test
    public fun testRemainingSleep(): TestResult = runTest(timeout = Duration.INFINITE) {
        val delay = AutomationDelay(seconds = 100L)

        clock.currentTimeMillis = 100 * 1000
        val job = startProcessing(delay, this, triggerTime = 50 * 1000)

        job.await()
        assertEquals(listOf(50L), sleepIntervals)
    }

    @Test
    public fun testSkipSleep(): TestResult = runTest {
        val delay = AutomationDelay(seconds = 100L)
        clock.currentTimeMillis = 100 * 1000
        val job = startProcessing(delay, this, triggerTime = 0)

        job.await()
        assertTrue(sleepIntervals.isEmpty())
    }

    @Test
    public fun testEmptyDelay(): TestResult = runTest {
        val delay = AutomationDelay()

        val job = startProcessing(delay, this)
        job.await()

        assertTrue(sleepIntervals.isEmpty())
        assertTrue(processor.areConditionsMet(delay))
    }

    @Test
    public fun testNilDelay(): TestResult = runTest {
        val job = startProcessing(null, this)
        job.await()
        assertTrue(sleepIntervals.isEmpty())
        assertTrue(processor.areConditionsMet(null))
    }

    @Test
    public fun testScreenConditions(): TestResult = runTest {
        val delay = AutomationDelay(screens = listOf("screen1", "screen2"))

        assertFalse(processor.areConditionsMet(delay))

        analytics.trackScreen("screen1")
        assertTrue(processor.areConditionsMet(delay))

        analytics.trackScreen("screen3")
        assertFalse(processor.areConditionsMet(delay))
    }

    @Test
    public fun testRegionCondition(): TestResult = runTest {
        val delay = AutomationDelay(regionID = "foo")
        assertFalse(processor.areConditionsMet(delay))

        trackRegionEnter("foo")
        assertTrue(processor.areConditionsMet(delay))

        trackRegionExit("foo")
        trackRegionEnter("bar")
        assertFalse(processor.areConditionsMet(delay))
    }

    @Test
    public fun testForegroundAppState(): TestResult = runTest {
        val delay = AutomationDelay(appState = AutomationAppState.FOREGROUND)

        activityMonitor.background()
        assertFalse(processor.areConditionsMet(delay))

        activityMonitor.foreground()
        assertTrue(processor.areConditionsMet(delay))
    }

    @Test
    public fun testBackgroundAppState(): TestResult = runTest {
        val delay = AutomationDelay(appState = AutomationAppState.BACKGROUND)

        activityMonitor.background()
        assertTrue(processor.areConditionsMet(delay))

        activityMonitor.foreground()
        assertFalse(processor.areConditionsMet(delay))
    }

    private suspend fun startProcessing(
        delay: AutomationDelay?,
        scope: TestScope,
        triggerTime: Long = clock.currentTimeMillis()
        ): Deferred<Unit> {

        val started = CompletableDeferred<Unit>()
        val otherDispatcher = StandardTestDispatcher(scope.testScheduler, "Processor Dispatcher")
        val result = scope.async(otherDispatcher) {
            started.complete(Unit)
            processor.process(delay, triggerDate = triggerTime)
        }

        started.await()
        yield()

        return result
    }

    private fun trackRegionEnter(regionId: String) {
        trackRegionEvent(regionId, RegionEvent.BOUNDARY_EVENT_ENTER)
    }

    private fun trackRegionExit(regionId: String) {
        trackRegionEvent(regionId, RegionEvent.BOUNDARY_EVENT_EXIT)
    }

    private fun trackRegionEvent(id: String, type: Int) {
        val regionEnter = RegionEvent
            .newBuilder()
            .setRegionId(id)
            .setBoundaryEvent(type)
            .setSource("test")
            .build()

        analytics.addEvent(regionEnter)
    }
}
