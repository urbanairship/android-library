/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AutoRefreshingDataProviderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = TestClock()
    private val ids = MutableStateFlow("initial-id")
    private val overrides = MutableStateFlow("initial-override")

    // We use a while loop to do waiting/backoff so we need a way to control when it
    // loops. Using a continueFlow to gate this.
    private val continueFlow = MutableSharedFlow<Unit>(replay = 0)
    private val sleeper = TestTaskSleeper(clock) { continueFlow.first() }
    private val provider = TestProvider(ids, overrides, clock, sleeper, testDispatcher)

    @Test
    fun testInitialFetch() = runTest(testDispatcher) {
        provider.updates.test {
            advanceUntilIdle()

            val item = awaitItem()
            assertEquals("initial-id", item.identifier)
            assertEquals("raw-data:initial-override", item.data.getOrNull())

            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, provider.fetchCount)
    }

    @Test
    fun testRefreshForcesNewFetch() = runTest(testDispatcher) {
        provider.updates.test {
            advanceUntilIdle()
            awaitItem()

            provider.refresh()
            advanceUntilIdle()

            awaitItem()
            assertEquals(2, provider.fetchCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testIdentifierChange() = runTest(testDispatcher) {
        provider.updates.test {
            advanceUntilIdle()
            awaitItem()

            ids.value = "id-2"
            advanceUntilIdle()

            val item = awaitItem()
            assertEquals("id-2", item.identifier)
            assertEquals(2, provider.fetchCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testBackoff() = runTest(testDispatcher) {
        // 1. Force the provider to fail
        val error = Exception("Network Error")
        provider.onFetchResult = Result.failure(error)

        provider.updates.test {
            // Failure 1
            advanceUntilIdle()
            assertTrue(awaitItem().data.isFailure)
            assertEquals(8.seconds, sleeper.sleeps[0])

            // Failure 2 (Backoff x2)
            continueFlow.emit(Unit)
            advanceUntilIdle()
            assertEquals(16.seconds, sleeper.sleeps[1])

            // Failure 3 (Backoff x4)
            continueFlow.emit(Unit)
            advanceUntilIdle()
            assertEquals(32.seconds, sleeper.sleeps[2])

            // Failure 4 (Backoff x8 - Hit Max)
            continueFlow.emit(Unit)
            advanceUntilIdle()
            assertEquals(64.seconds, sleeper.sleeps[3])

            // Failure 5 (Still Capped at Max)
            continueFlow.emit(Unit)
            advanceUntilIdle()
            assertEquals(64.seconds, sleeper.sleeps[4])

            assertEquals(5, sleeper.sleeps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testBackoffResetsOnSuccess() = runTest(testDispatcher) {
        provider.onFetchResult = Result.failure(Exception("Fail"))

        provider.updates.test {
            // First failure triggers 8s backoff
            advanceUntilIdle()
            awaitItem()
            assertEquals(8.seconds, sleeper.sleeps[0])

            // Now trigger a success
            provider.onFetchResult = Result.success("Back to normal")
            continueFlow.emit(Unit)
            advanceUntilIdle()
            awaitItem()

            // Cache sleep
            assertEquals(600.seconds, sleeper.sleeps[1])

            // Expire the cache
            clock.currentTimeMillis += 10.minutes.inWholeMilliseconds

            // Trigger another failure
            provider.onFetchResult = Result.failure(Exception("New Fail"))
            continueFlow.emit(Unit)
            advanceUntilIdle()

            // This should be 8.seconds again, NOT 16.seconds
            assertEquals(8.seconds, sleeper.sleeps[2])

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNoFailureEmittedAfterSuccess() = runTest(testDispatcher) {
        provider.onFetchResult = Result.success("initial-success")

        provider.updates.test {
            advanceUntilIdle()
            awaitItem() // Consume initial success
            assertEquals(1, provider.fetchCount)

            // Expire the cache
            clock.currentTimeMillis += 10.minutes.inWholeMilliseconds

            provider.onFetchResult = Result.failure(Exception("Silent Error"))
            continueFlow.emit(Unit)
            advanceUntilIdle()

            // Verify the fetch actually happened
            assertEquals(2, provider.fetchCount)

            // Verify that despite the fetch happening, no new flow emission occurred
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    private class TestProvider(
        identifierUpdates: MutableStateFlow<String>,
        overrideUpdates: MutableStateFlow<String>,
        clock: Clock,
        sleeper: TaskSleeper,
        dispatcher: TestDispatcher
    ) : AutoRefreshingDataProvider<String, String>(
        identifierUpdates, overrideUpdates, clock, sleeper, dispatcher
    ) {

        var fetchCount = 0

        var onFetchResult: Result<String> = Result.success("raw-data")

        override suspend fun onFetch(identifier: String): Result<String> {
            fetchCount++
            return onFetchResult
        }

        override fun onApplyOverrides(data: String, overrides: String): String {
            return "$data:$overrides"
        }
    }
}
