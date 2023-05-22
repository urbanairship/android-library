package com.urbanairship.liveupdate

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.liveupdate.LiveUpdateProcessor.Operation
import com.urbanairship.liveupdate.data.LiveUpdateContent
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateState
import com.urbanairship.liveupdate.data.LiveUpdateStateWithContent
import com.urbanairship.liveupdate.util.jsonMapOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class LiveUpdateProcessorTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dao: LiveUpdateDao = mockk(relaxed = true, relaxUnitFun = true)

    private lateinit var processor: LiveUpdateProcessor

    @Before
    public fun setUp() {
        processor = LiveUpdateProcessor(dao, testDispatcher)
    }

    @Test
    public fun testStart(): TestResult = runTest(testDispatcher) {
        val content = jsonMapOf("foo" to "bar")

        coEvery { dao.getState(eq("name")) } returns LiveUpdateState(
            name = "name",
            type = "type",
            timestamp = 0,
            dismissalDate = null,
            isActive = false
        )

        processor.handlerCallbacks.test {
            processor.enqueue(
                Operation.Start(
                    name = "name",
                    type = "type",
                    content = content,
                    timestamp = 0,
                    dismissalTimestamp = null
                )
            )
            println("enqueued...")

            awaitItem().let {
                println("gotItem = $it")

                assertTrue(it.action == LiveUpdateEvent.START)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
                assertEquals(content, it.update.content)
            }

            coVerifySequence {
                dao.getState(eq("name"))
                dao.upsert(
                    eq(
                        LiveUpdateState(
                            name = "name",
                            type = "type",
                            timestamp = 0,
                            dismissalDate = null,
                            isActive = true
                        )
                    ),
                    eq(
                        LiveUpdateContent(
                            name = "name",
                            content = content,
                            timestamp = 0,
                        )
                    )
                )
            }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testUpdateVerifiesTimestamps(): TestResult = runTest(testDispatcher) {
        fun testContent(value: Int, timestamp: Long) = LiveUpdateContent(
            name = "name",
            content = jsonMapOf("foo" to value),
            timestamp = timestamp
        )
        val initialContent = testContent(0, 0L)
        val staleContent = testContent(2, 50L)
        val updatedContent = testContent(3, 100L)

        // Initial mocks
        coEvery { dao.get(eq("name")) } returns LiveUpdateStateWithContent(
            LiveUpdateState(
                name = "name",
                type = "type",
                timestamp = initialContent.timestamp,
                dismissalDate = null,
                isActive = true
            ),
            LiveUpdateContent(
                name = "name",
                content = initialContent.content,
                timestamp = initialContent.timestamp
            )
        )

        processor.handlerCallbacks.test {
            // Enqueue initial update
            processor.enqueue(
                Operation.Update(
                    updatedContent.name,
                    updatedContent.content,
                    updatedContent.timestamp
                )
            )

            awaitItem().let {
                assertTrue(it.action == LiveUpdateEvent.UPDATE)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
                assertEquals(updatedContent.content, it.update.content)
            }

            advanceUntilIdle()
            ensureAllEventsConsumed()

            coVerifySequence {
                dao.get(eq("name"))
                dao.upsert(state = any(), content = eq(updatedContent))
            }

            // Reset mocks to reflect the updated content/timestamp
            clearMocks(dao)
            coEvery { dao.get(eq("name")) } returns LiveUpdateStateWithContent(
                LiveUpdateState(
                    name = "name",
                    type = "type",
                    timestamp = initialContent.timestamp,
                    dismissalDate = null,
                    isActive = true
                ),
                LiveUpdateContent(
                    name = "name",
                    content = updatedContent.content,
                    timestamp = updatedContent.timestamp
                )
            )

            // Enqueue stale update
            processor.enqueue(Operation.Update("name", staleContent.content, staleContent.timestamp))

            advanceUntilIdle()
            ensureAllEventsConsumed()

            // Ensure we read the existing record from the db (to get the timestamp)
            coVerifySequence {
                dao.get(eq("name"))
            }
        }
    }

    @Test
    public fun testHandleUpdate(): TestResult = runTest(testDispatcher) {
        val initial = jsonMapOf("foo" to "bar")
        val updated = jsonMapOf("fizz" to "buzz")

        val initialState = LiveUpdateState(
                name = "name",
                type = "type",
                timestamp = 0,
                dismissalDate = null,
                isActive = true
            )
        val initialContent = LiveUpdateContent(
                name = "name",
                content = initial,
                timestamp = 0,
            )
        val initialLiveUpdate = LiveUpdateStateWithContent(initialState, initialContent)

        val updatedContent = initialContent.copy(
            content = updated,
            timestamp = 10,
        )

        coEvery { dao.get(eq("name")) } returns initialLiveUpdate

        processor.handlerCallbacks.test {
            processor.enqueue(
                Operation.Update(
                    name = "name",
                    content = updated,
                    timestamp = 10,
                )
            )

            awaitItem().let {
                assertTrue(it.action == LiveUpdateEvent.UPDATE)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
                assertEquals(updated, it.update.content)
            }

            coVerifySequence {
                dao.get(eq("name"))
                dao.upsert(state = eq(initialState), content = eq(updatedContent))
            }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testStop(): TestResult = runTest(testDispatcher) {
        val state = LiveUpdateState(
            name = "name",
            type = "type",
            timestamp = 0,
            dismissalDate = null,
            isActive = true
        )
        val content = LiveUpdateContent(
            name = "name",
            content = jsonMapOf("foo" to "bar"),
            timestamp = 0,
        )
        val liveUpdate = LiveUpdateStateWithContent(state = state, content = content)

        val stopTime = 10L
        val stopState = state.copy(
            isActive = false,
            timestamp = stopTime
        )
        val stopContent = content.copy(
            content = jsonMapOf("fizz" to "buzz"),
            timestamp = stopTime
        )

        coEvery { dao.isAnyActive() } returns false
        coEvery { dao.get(eq("name")) } returns liveUpdate

        processor.handlerCallbacks.test {

            processor.enqueue(
                Operation.Stop(name = "name", content = stopContent.content, timestamp = stopTime)
            )

            awaitItem().let {
                assertTrue(it.action == LiveUpdateEvent.END)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
            }

            coVerifySequence {
                dao.get(eq("name"))
                dao.upsert(eq(stopState), eq(stopContent))
                dao.deleteContent(eq("name"))
                dao.isAnyActive()
            }

            advanceUntilIdle()
            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testStartUpdateStop(): TestResult = runTest(testDispatcher) {
        val initialState = LiveUpdateState(
            name = "name",
            type = "type",
            timestamp = 0,
            dismissalDate = 1000,
            isActive = false
        )
        val initialContent = LiveUpdateContent(
            name = "name",
            content = jsonMapOf("foo" to "bar"),
            timestamp = 0,
        )
        val initialLiveUpdate = LiveUpdateStateWithContent(initialState, initialContent)

        val startedState = initialState.copy(isActive = true)
        val startedContent = initialContent.copy()
        val startedLiveUpdate = LiveUpdateStateWithContent(startedState, startedContent)

        val updatedState = startedState.copy()
        val updatedContent = startedContent.copy(
            content = jsonMapOf("fizz" to "buzz"),
            timestamp = 10
        )
        val updatedLiveUpdate = LiveUpdateStateWithContent(updatedState, updatedContent)

        val stoppedState = updatedState.copy(isActive = false, timestamp = 20, dismissalDate = 2000)
        val stoppedContent = updatedContent.copy(
            content = jsonMapOf("slim" to "none"),
            timestamp = 20
        )

        // Mock initial state
        coEvery { dao.getState(eq("name")) } returns initialLiveUpdate.state

        processor.handlerCallbacks.test {
            // Enqueue start operation
            processor.enqueue(
                Operation.Start(
                    name = "name",
                    type = "type",
                    content = initialContent.content,
                    timestamp = initialContent.timestamp,
                    dismissalTimestamp = initialState.dismissalDate
                )
            )

            // Verify start effect
            awaitItem().let {
                println("gotItem = $it")

                assertTrue(it.action == LiveUpdateEvent.START)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
                assertEquals(initialContent.content, it.update.content)
                assertEquals(initialState.dismissalDate, it.update.dismissalTime)
            }

            // Verify insert dao calls
            coVerifyOrder {
                dao.getState(eq("name"))
                dao.upsert(any(), any())
            }

            // We shouldn't have any additional effects
            ensureAllEventsConsumed()

            // Reset mocks for update operation
            clearMocks(dao)
            coEvery { dao.get(eq("name")) } returns startedLiveUpdate
            coEvery { dao.getState(eq("name")) } returns startedLiveUpdate.state

            // Enqueue update
            processor.enqueue(
                Operation.Update(
                    name = "name",
                    content = updatedContent.content,
                    timestamp = updatedContent.timestamp,
                )
            )

            // Verify update effect
            awaitItem().let {
                assertTrue(it.action == LiveUpdateEvent.UPDATE)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
                assertEquals(updatedContent.content, it.update.content)
            }

            // Verify update dao calls
            coVerifySequence {
                dao.get(eq("name"))
                dao.upsert(state = eq(updatedState), content = eq(updatedContent))
            }

            // We shouldn't have any additional effects
            ensureAllEventsConsumed()

            // Reset mocks for stop operation
            clearMocks(dao)
            coEvery { dao.isAnyActive() } returns false
            coEvery { dao.get(eq("name")) } returns updatedLiveUpdate
            coEvery { dao.getState(eq("name")) } returns updatedLiveUpdate.state

            // Enqueue stop (with an update to the dismissal timestamp)
            processor.enqueue(
                Operation.Stop(
                    name = "name",
                    content = stoppedContent.content,
                    timestamp = stoppedContent.timestamp,
                    dismissalTimestamp = stoppedState.dismissalDate
                )
            )

            // Verify stop effect
            awaitItem().let {
                assertTrue(it.action == LiveUpdateEvent.END)

                assertEquals("name", it.update.name)
                assertEquals("type", it.update.type)
                assertEquals(stoppedContent.content, it.update.content)
                assertEquals(stoppedState.dismissalDate, it.update.dismissalTime)
            }

            // Verify stop dao calls
            coVerifySequence {
                dao.get(eq("name"))
                dao.upsert(state = eq(stoppedState), content = eq(stoppedContent))
                dao.deleteContent(eq("name"))
                dao.isAnyActive()
            }

            // Run until idle and ensure no additional effects are emitted
            advanceUntilIdle()
            ensureAllEventsConsumed()
        }
    }
}
