package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class TaskSleeperTest {
    private val testDispatcher = StandardTestDispatcher()

    private val clock: TestClock = TestClock().apply {
        currentTimeMillis = 0
    }

    private val recordedIntervals = mutableListOf<Duration>()

    // We create a subclass to override onSleep and capture the chunks
    // without actually calling delay()
    private val sleeper = object : TaskSleeper(clock) {
        override suspend fun onSleep(duration: Duration) {
            recordedIntervals.add(duration)
            // Manually advance our test clock so the 'remainingMillis' calculation works
            clock.currentTimeMillis += duration.inWholeMilliseconds
        }
    }

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testIntervalSleep(): TestResult = runTest(testDispatcher) {
        // This tests that 85s is broken into [30s, 30s, 25s]
        sleeper.sleep(85.seconds)

        assertThat(recordedIntervals).containsExactly(30.seconds, 30.seconds, 25.seconds)
    }

    @Test
    public fun testBelowIntervalSleep(): TestResult = runTest(testDispatcher) {
        // This tests that 10s is just one [10s] chunk
        sleeper.sleep(10.seconds)

        assertThat(recordedIntervals).containsExactly(10.seconds)
    }

    @Test
    public fun testNegativeSleep(): TestResult = runTest(testDispatcher) {
        sleeper.sleep((-5).seconds)

        assertThat(recordedIntervals).isEmpty()
    }

    @Test
    public fun testZeroSleep(): TestResult = runTest(testDispatcher) {
        sleeper.sleep(0.seconds)

        assertThat(recordedIntervals).isEmpty()
    }
}
