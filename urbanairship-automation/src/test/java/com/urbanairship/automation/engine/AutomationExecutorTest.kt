package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.AutomationExecutor
import com.urbanairship.automation.AutomationExecutorDelegate
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InterruptedBehavior
import com.urbanairship.automation.ScheduleExecuteResult
import com.urbanairship.automation.ScheduleReadyResult
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.json.JsonValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationExecutorTest {
    private val actionExecutor: AutomationExecutorDelegate<JsonValue> = mockk()
    private val messageExecutor: AutomationExecutorDelegate<PreparedInAppMessageData> = mockk()
    private val remoteDataAccess: AutomationRemoteDataAccess = mockk()

    private val preparedMessageData = PreparedInAppMessageData(
        message = InAppMessage(
            name = "message name",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom")))
        ),
        displayAdapter = mockk(),
        displayCoordinator = mockk()
    )

    private val executor = AutomationExecutor(actionExecutor, messageExecutor, remoteDataAccess)

    @Test
    public fun testMessageIsReady(): TestResult = runTest {
        val schedule = makeSchedule(data = PreparedScheduleData.InAppMessage(preparedMessageData))

        ScheduleReadyResult.entries.forEach { result ->
            coEvery { messageExecutor.isReady(any(), any()) } answers {
                assertEquals(schedule.data, PreparedScheduleData.InAppMessage(firstArg()))
                assertEquals(schedule.info, secondArg())
                result
            }

            val readyResult = executor.isReady(schedule)
            assertEquals(result, readyResult)
            coVerify { messageExecutor.isReady(
                data = eq(preparedMessageData),
                preparedScheduleInfo = eq(schedule.info))
            }
        }
    }

    @Test
    public fun testActionIsReady(): TestResult = runTest {
        val schedule = makeSchedule()

        ScheduleReadyResult.entries.forEach { result ->
            coEvery { actionExecutor.isReady(any(), any()) } answers {
                assertEquals(schedule.data, PreparedScheduleData.Action(firstArg()))
                assertEquals(schedule.info, secondArg())
                result
            }

            val readyResult = executor.isReady(schedule)
            assertEquals(result, readyResult)
            coVerify { actionExecutor.isReady(
                data = eq(JsonValue.wrap("neat")),
                preparedScheduleInfo = eq(schedule.info))
            }
        }
    }

    @Test
    public fun testFrequencyCheckerCheckFailed(): TestResult = runTest {
        val frequencyChecker: FrequencyChecker = mockk()
        coEvery { frequencyChecker.checkAndIncrement() } returns false

        val schedule = makeSchedule(checker = frequencyChecker)

        assertEquals(executor.isReady(schedule), ScheduleReadyResult.SKIP)
        coVerify { frequencyChecker.checkAndIncrement() }
    }

    @Test
    public fun testFrequencyCheckerCheckSuccess(): TestResult = runTest {
        val frequencyChecker: FrequencyChecker = mockk()
        coEvery { frequencyChecker.checkAndIncrement() } returns true

        coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

        val schedule = makeSchedule(checker = frequencyChecker)

        assertEquals(executor.isReady(schedule), ScheduleReadyResult.READY)
        coVerify { frequencyChecker.checkAndIncrement() }
        coVerify { actionExecutor.isReady(
            data = eq(JsonValue.wrap("neat")),
            preparedScheduleInfo = eq(schedule.info))
        }
    }

    @Test
    public fun testIsReadyPrecheckCurrent(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = "some id",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.NULL),
            created = 0U
        )

        coEvery { remoteDataAccess.isCurrent(eq(schedule)) } returns true

        assertEquals(executor.isReadyPrecheck(schedule), ScheduleReadyResult.READY)
        coVerify { remoteDataAccess.isCurrent(eq(schedule)) }
    }

    @Test
    public fun testIsReadyPrecheckNotCurrent(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = "some id",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.NULL),
            created = 0U
        )

        coEvery { remoteDataAccess.isCurrent(any()) } returns false

        assertEquals(executor.isReadyPrecheck(schedule), ScheduleReadyResult.INVALIDATE)
        coVerify { remoteDataAccess.isCurrent(eq(schedule)) }
    }

    @Test
    public fun testExecuteActions():TestResult = runTest {
        val schedule = makeSchedule()

        coEvery { actionExecutor.execute(any(), any()) } answers  {
            assertEquals(schedule.data, PreparedScheduleData.Action(firstArg()))
            assertEquals(schedule.info, secondArg())
            ScheduleExecuteResult.FINISHED
        }

        assertEquals(ScheduleExecuteResult.FINISHED, executor.execute(schedule))
        coVerify {
            actionExecutor.execute(
                data = eq(JsonValue.wrap("neat")),
                preparedScheduleInfo = eq(schedule.info)
            )
        }
    }

    @Test
    public fun testExecuteMessage():TestResult = runTest {
        val schedule = makeSchedule(data = PreparedScheduleData.InAppMessage(preparedMessageData))

        coEvery { messageExecutor.execute(any(), any()) } answers  {
            assertEquals(schedule.data, PreparedScheduleData.InAppMessage(firstArg()))
            assertEquals(schedule.info, secondArg())
            ScheduleExecuteResult.FINISHED
        }

        assertEquals(ScheduleExecuteResult.FINISHED, executor.execute(schedule))
        coVerify {
            messageExecutor.execute(
                data = eq(preparedMessageData),
                preparedScheduleInfo = eq(schedule.info)
            )
        }
    }

    @Test
    public fun testExecuteDelegateThrows(): TestResult = runTest {
        val schedule = makeSchedule(data = PreparedScheduleData.InAppMessage(preparedMessageData))

        coEvery { messageExecutor.execute(any(), any()) } answers  {
            throw IllegalArgumentException()
        }

        assertEquals(ScheduleExecuteResult.RETRY, executor.execute(schedule))
        coVerify {
            messageExecutor.execute(
                data = eq(preparedMessageData),
                preparedScheduleInfo = eq(schedule.info)
            )
        }
    }

    @Test
    public fun testInterruptedAction(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = "some id",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("neat")),
            created = 0U
        )

        val preparedScheduleInfo = PreparedScheduleInfo("prepared schedule id")

        coEvery { actionExecutor.interrupted(any(), any()) } answers {
            assertEquals(schedule, firstArg())
            assertEquals(preparedScheduleInfo, secondArg())
            InterruptedBehavior.RETRY
        }

        assertEquals(InterruptedBehavior.RETRY, executor.interrupted(schedule, preparedScheduleInfo))
        coVerify { actionExecutor.interrupted(eq(schedule), eq(preparedScheduleInfo)) }
    }

    @Test
    public fun testInterruptedMessage(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = "some id",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.InAppMessageData(
                InAppMessage(
                    name = "message name",
                    displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL))
                )
            ),
            created = 0U
        )

        val preparedScheduleInfo = PreparedScheduleInfo("prepared schedule id")

        coEvery { messageExecutor.interrupted(any(), any()) } answers {
            assertEquals(schedule, firstArg())
            assertEquals(preparedScheduleInfo, secondArg())
            InterruptedBehavior.FINISH
        }

        assertEquals(InterruptedBehavior.FINISH, executor.interrupted(schedule, preparedScheduleInfo))
        coVerify { messageExecutor.interrupted(eq(schedule), eq(preparedScheduleInfo)) }
    }

    private fun makeSchedule(data: PreparedScheduleData? = null, checker: FrequencyChecker? = null): PreparedSchedule {
        val scheduleData = data ?: PreparedScheduleData.Action(JsonValue.wrap("neat"))

        return PreparedSchedule(
            info = PreparedScheduleInfo("schedule id"),
            data = scheduleData,
            frequencyChecker = checker
        )
    }
}
