/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.util.TaskSleeper
import java.util.Date
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private typealias EvaluationClosure = () -> ExecutionWindowResult

@RunWith(AndroidJUnit4::class)
public class ExecutionWindowProcessorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock().apply { currentTimeMillis = 0 }

    private val sleeper: TaskSleeper = mockk()

    private val window = ExecutionWindow(
        includes = listOf(Rule.Weekly(daysOfWeek = listOf(1)))
    )
    private lateinit var processor: ExecutionWindowProcessor

    private val evaluatedWindows = MutableStateFlow<List<Pair<Date, ExecutionWindow>>>(emptyList())
    private val onResult = MutableStateFlow<EvaluationClosure?>(null)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    public fun setup() {
        processor = ExecutionWindowProcessor(
            context = context,
            taskSleeper = sleeper,
            clock = clock,
            onEvaluate = { window, date ->
                evaluatedWindows.update { it + Pair(date, window) }
                onResult.value!!.invoke()
            },
            dispatcher = testDispatcher
        )

        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testIsAvailable(): TestResult = runTest {
        onResult.update { { throw IllegalArgumentException() } }
        assertFalse(processor.isActive(window))

        onResult.update { { ExecutionWindowResult.Retry(100.seconds) } }
        assertFalse(processor.isActive(window))

        onResult.update { { ExecutionWindowResult.Now } }
        assertTrue(processor.isActive(window))

        val evaluated = Pair(Date(clock.currentTimeMillis), window)
        assertEquals(evaluatedWindows.value, listOf(evaluated, evaluated, evaluated))
    }

    @Test
    public fun testProcessError(): TestResult = runTest {
        onResult.update { { throw IllegalArgumentException() } }

        val job = launch {
            processor.process(window)
        }

        coEvery { sleeper.sleep(any()) } answers {
            job.cancel()
        }

        job.join()

        coVerify { sleeper.sleep(1.days) }

        val evaluated = Pair(Date(clock.currentTimeMillis), window)
        assertEquals(evaluatedWindows.value, listOf(evaluated))
    }

    @Test
    public fun testProcessRetry(): TestResult = runTest {
        onResult.update { { ExecutionWindowResult.Retry(100.seconds) } }

        val job = launch {
            processor.process(window)
        }

        coEvery { sleeper.sleep(any()) } answers {
            job.cancel()
        }

        job.join()

        coVerify { sleeper.sleep(100.seconds) }

        val evaluated = Pair(Date(clock.currentTimeMillis), window)
        assertEquals(evaluatedWindows.value, listOf(evaluated))
    }

    @Test
    public fun testHappyPath(): TestResult = runTest {
        onResult.update { { ExecutionWindowResult.Now } }

        withTimeout(1.seconds) {
            processor.process(window)
        }

        val evaluated = Pair(Date(clock.currentTimeMillis), window)
        assertEquals(evaluatedWindows.value, listOf(evaluated))
    }

    @Test
    public fun testLocaleChangeRechecks(): TestResult = runTest {

        onResult.update { { ExecutionWindowResult.Retry(100.seconds) } }

        val job = launch {
            processor.process(window)
        }

        var sleepCalls = 0
        coEvery { sleeper.sleep(any()) } coAnswers {
            sleepCalls += 1
            if (sleepCalls < 2) {
                delay(1.seconds)
            } else {
                job.cancel()
            }
        }

        context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))

        job.join()

        coVerify(exactly = 2) { sleeper.sleep(100.seconds) }

        val evaluated = Pair(Date(clock.currentTimeMillis), window)
        assertEquals(evaluatedWindows.value, listOf(evaluated, evaluated))
    }
}
