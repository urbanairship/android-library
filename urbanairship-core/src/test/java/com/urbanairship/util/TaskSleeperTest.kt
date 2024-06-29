package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val sleeper: TestTaskSleeper = TestTaskSleeper(clock) { sleep ->
        clock.currentTimeMillis += sleep.inWholeMilliseconds
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
    public fun testIntervalSleep(): TestResult = runTest {
        awaitSleep(85.seconds)
        assertThat(sleeper.sleeps).containsExactly(30.seconds, 30.seconds, 25.seconds)
    }

    @Test
    public fun testBelowIntervalSleep(): TestResult = runTest {
        awaitSleep(30.seconds)
        assertThat(sleeper.sleeps).containsExactly(30.seconds)
    }

    @Test
    public fun testNegativeSleep(): TestResult = runTest {
        awaitSleep((-1).seconds)
        assertThat(sleeper.sleeps).isEmpty()
    }

    @Test
    public fun testZeroSleep(): TestResult = runTest {
        awaitSleep(0.seconds)
        assertThat(sleeper.sleeps).isEmpty()
    }

    private suspend fun awaitSleep(duration: Duration) {
        coroutineScope {
            async { sleeper.sleep(duration) }.await()
        }
    }
}
