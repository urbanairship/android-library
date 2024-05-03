package com.urbanairship.automation.rewrite.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RetryingQueueTest {

    private val sleeper: TaskSleeper = mockk(relaxed = true)
    private val queue = RetryingQueue(taskSleeper = sleeper)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testResultOrder(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val results = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)

        val taskFlows = listOf<MutableSharedFlow<RetryingQueue.Result<Unit>?>>(
            MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE),
            MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE),
            MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE),
            MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE),
            MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE)
        )

        taskFlows.forEachIndexed { index, flow ->
            launch {
                val name = "operation $index"
                queue.run(name) {
                    val result = flow.filterNotNull().first()
                    result
                }
                results.emit(name)
            }
        }

        results.test {
            expectNoEvents()

            // Finish 0
            taskFlows[0].emit(RetryingQueue.Result.Success(Unit))
            assertEquals("operation 0", awaitItem())
            expectNoEvents()

            // Finish 2
            taskFlows[2].emit(RetryingQueue.Result.Success(Unit))
            expectNoEvents()

            // Retry 1, should cause 2 to return
            taskFlows[1].emit(RetryingQueue.Result.Retry())
            assertEquals("operation 2", awaitItem())
            expectNoEvents()

            // Finish 4 with ignore return order
            taskFlows[4].emit(RetryingQueue.Result.Success(Unit, ignoreReturnOrder = true))
            assertEquals("operation 4", awaitItem())
            expectNoEvents()

            // Retry 3, then finish 3, should be blocked on 1
            taskFlows[3].emit(RetryingQueue.Result.Retry())
            taskFlows[3].emit(RetryingQueue.Result.Success(Unit))
            expectNoEvents()

            // Finish 1
            taskFlows[1].emit(RetryingQueue.Result.Success(Unit))
            assertEquals("operation 1", awaitItem())
            assertEquals("operation 3", awaitItem())
        }

        coVerify {
            sleeper.sleep(15.seconds)
            sleeper.sleep(15.seconds)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testBackOff(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val results = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)

        val taskResults = MutableSharedFlow<RetryingQueue.Result<Unit>?>(
            extraBufferCapacity = Int.MAX_VALUE
        )

        launch {
            queue.run("test") {
                taskResults.filterNotNull().first()
            }
            results.emit("finished")
        }

        results.test {
            expectNoEvents()

            taskResults.emit(RetryingQueue.Result.Retry())
            taskResults.emit(RetryingQueue.Result.Retry())
            taskResults.emit(RetryingQueue.Result.Retry())
            taskResults.emit(RetryingQueue.Result.Retry())

            taskResults.emit(RetryingQueue.Result.Retry(retryAfter = 10000.seconds))
            taskResults.emit(RetryingQueue.Result.Retry())

            taskResults.emit(RetryingQueue.Result.Retry(retryAfter = INFINITE))
            taskResults.emit(RetryingQueue.Result.Retry())

            taskResults.emit(RetryingQueue.Result.Retry(retryAfter = ZERO))
            taskResults.emit(RetryingQueue.Result.Retry())

            expectNoEvents()
            taskResults.emit(RetryingQueue.Result.Success(Unit))
            assertEquals("finished", awaitItem())
        }

        coVerify {
            sleeper.sleep(15.seconds)
            sleeper.sleep(30.seconds)
            sleeper.sleep(60.seconds)
            sleeper.sleep(60.seconds)
            sleeper.sleep(10000.seconds)
            sleeper.sleep(60.seconds)
            sleeper.sleep(60.seconds)
            sleeper.sleep(60.seconds)
            sleeper.sleep(0.seconds)
            sleeper.sleep(15.seconds)
        }
    }
}
