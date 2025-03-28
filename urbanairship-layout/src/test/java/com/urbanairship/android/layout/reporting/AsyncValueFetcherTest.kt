/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AsyncValueFetcherTest {
    private val clock: TestClock = mockk()
    private lateinit var taskSleeper: TestTaskSleeper
    private var currentTime = 1L

    @Before
    public fun setUp() {
        taskSleeper = TestTaskSleeper(clock) { delay -> currentTime += delay.inWholeMilliseconds}
        every { clock.currentTimeMillis() } answers { currentTime }
    }

    @Test
    public fun testProcessingEarlyProcessDelay(): TestResult = runTest {
        val result = makeFetcher(
            block = { defaultFetchBlock(null) },
            delay = 20.seconds
        )
            .fetch(this, retryErrors = false)

        assertEquals(listOf(20.seconds), taskSleeper.sleeps)
        assertTrue(result.isInvalid)
    }

    @Test
    public fun testInvalidResultDoesNotRetry(): TestResult = runTest {
        val fetcher = makeFetcher(
            block = { defaultFetchBlock(null) },
            delay = 1.seconds
        )

        val result = fetcher.fetch(this, retryErrors = false)

        fetcher.fetch(this, retryErrors = true)
        fetcher.fetch(this, retryErrors = true)
        fetcher.fetch(this, retryErrors = true)

        assertEquals(listOf(1.seconds), taskSleeper.sleeps)
        assertTrue(result.isInvalid)
    }

    @Test
    public fun testValidResultDoesNotRetry(): TestResult = runTest {
        val expected = defaultFetchBlock("valid")
        val fetcher = makeFetcher(
            block = { expected },
            delay = 1.seconds
        )

        val result = fetcher.fetch(this, retryErrors = false)

        fetcher.fetch(this, retryErrors = true)
        fetcher.fetch(this, retryErrors = true)
        fetcher.fetch(this, retryErrors = true)

        assertEquals(listOf(1.seconds), taskSleeper.sleeps)
        assertEquals(result, expected)
    }

    @Test
    public fun testErrorRetries(): TestResult = runTest {
        val expected = ThomasFormField.AsyncValueFetcher.PendingResult.Error<String>()
        val fetcher = makeFetcher(
            block = { expected },
            delay = 1.seconds
        )

        val result = fetcher.fetch(this, retryErrors = false)

        fetcher.fetch(this, retryErrors = true)
        fetcher.fetch(this, retryErrors = true)

        assertEquals(listOf(1.seconds, 3.seconds, 6.seconds), taskSleeper.sleeps)
        assertEquals(result, expected)
    }

    @Test
    public fun testAsyncValidationError(): TestResult = runTest {
        val expected = ThomasFormField.AsyncValueFetcher.PendingResult.Error<String>()
        val fetcher = makeFetcher(
            block = { expected },
            delay = 1.seconds
        )

        assertEquals(taskSleeper.sleeps, emptyList<Duration>())

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1.seconds))

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3).map { it.seconds })

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3, 6).map { it.seconds })

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3, 6, 12).map { it.seconds })

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3, 6, 12, 15).map { it.seconds })

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3, 6, 12, 15, 15).map { it.seconds })

        currentTime += 10.seconds.inWholeMilliseconds
        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3, 6, 12, 15, 15, 5).map { it.seconds })

        fetcher.fetch(this, retryErrors = true)
        assertEquals(taskSleeper.sleeps, listOf(1, 3, 6, 12, 15, 15, 5, 15).map { it.seconds })
    }

    @Test
    public fun testUpdates(): TestResult = runTest {
        val states = mutableListOf(
            ThomasFormField.AsyncValueFetcher.PendingResult.Error<String>(),
            ThomasFormField.AsyncValueFetcher.PendingResult.Error<String>(),
            ThomasFormField.AsyncValueFetcher.PendingResult.Invalid<String>()
        )

        val fetcher = makeFetcher(
            block = { states.removeFirst() },
            delay = 1.seconds
        )

        val scope = this

        fetcher.results.test {
            awaitItem()

            fetcher.fetch(scope, retryErrors = true)
            assertTrue(awaitItem()?.isError == true)

            fetcher.fetch(scope, retryErrors = true)
            assertTrue(awaitItem()?.isError == true)

            fetcher.fetch(scope, retryErrors = true)
            assertTrue(awaitItem()?.isInvalid == true)
        }
    }

    private fun defaultFetchBlock(result: String?): ThomasFormField.AsyncValueFetcher.PendingResult<String> {
        return if (result == null) {
            ThomasFormField.AsyncValueFetcher.PendingResult.Invalid()
        } else {
            ThomasFormField.AsyncValueFetcher.PendingResult.Valid(
                result = ThomasFormField.Result(result)
            )
        }
    }

    private fun makeFetcher(
        block: suspend () -> ThomasFormField.AsyncValueFetcher.PendingResult<String>,
        delay: Duration,
    ): ThomasFormField.AsyncValueFetcher<String> {
        return ThomasFormField.AsyncValueFetcher(
            fetchBlock = block,
            processDelay = delay,
            clock = clock,
            taskSleeper = taskSleeper
        )
    }
}
