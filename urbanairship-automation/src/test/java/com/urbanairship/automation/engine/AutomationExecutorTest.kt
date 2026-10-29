package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.audience.VariantAudience
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonValue
import java.time.Instant
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationExecutorTest {
    private val actionExecutor: AutomationExecutorDelegate<JsonValue> = mockk()
    private val messageExecutor: AutomationExecutorDelegate<PreparedInAppMessageData> = mockk()
    private val remoteDataAccess: AutomationRemoteDataAccess = mockk()
    private val messageAnalytics: InAppMessageAnalyticsInterface = mockk()

    private val preparedMessageData = PreparedInAppMessageData(
        message = InAppMessage(
            name = "message name",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom")))
        ),
        displayAdapter = mockk(),
        displayCoordinator = mockk(),
        analytics = messageAnalytics,
        actionRunner = mockk()
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
        coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY
        coEvery { frequencyChecker.checkAndIncrement() } returns false

        val schedule = makeSchedule(checker = frequencyChecker)

        assertEquals(ScheduleReadyResult.SKIP, executor.isReady(schedule))
        coVerify { frequencyChecker.checkAndIncrement() }
    }

    @Test
    public fun testFrequencyCheckerCheckSuccess(): TestResult = runTest {
        val frequencyChecker: FrequencyChecker = mockk()
        coEvery { frequencyChecker.checkAndIncrement() } returns true

        coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

        val schedule = makeSchedule(checker = frequencyChecker)

        assertEquals(ScheduleReadyResult.READY, executor.isReady(schedule))
        coVerify { frequencyChecker.checkAndIncrement() }
        coVerify { actionExecutor.isReady(
            data = eq(JsonValue.wrap("neat")),
            preparedScheduleInfo = eq(schedule.info))
        }
    }

    @Test
    public fun testFrequencyCheckerSkippedForVariantNoMessageOutcomes(): TestResult = runTest {
        listOf(VariantAudience.Outcome.HOLDOUT, VariantAudience.Outcome.VARIANT_MISS).forEach { outcome ->
            val frequencyChecker: FrequencyChecker = mockk()
            coEvery { frequencyChecker.checkAndIncrement() } returns false
            coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

            val schedule = makeSchedule(
                checker = frequencyChecker,
                info = makeInfo(variantAudienceResult = VariantAudienceResult(outcome))
            )

            assertEquals(ScheduleReadyResult.READY, executor.isReady(schedule))
            coVerify(exactly = 0) { frequencyChecker.checkAndIncrement() }
        }
    }

    @Test
    public fun testFrequencyCheckerChargedForVariantMatch(): TestResult = runTest {
        val frequencyChecker: FrequencyChecker = mockk()
        coEvery { frequencyChecker.checkAndIncrement() } returns true
        coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

        val schedule = makeSchedule(
            checker = frequencyChecker,
            info = makeInfo(variantAudienceResult = VariantAudienceResult(VariantAudience.Outcome.MATCHED))
        )

        assertEquals(ScheduleReadyResult.READY, executor.isReady(schedule))
        coVerify(exactly = 1) { frequencyChecker.checkAndIncrement() }
    }

    /** The experiment evaluator returns a result whenever experiments exist, matching or not. */
    @Test
    public fun testFrequencyCheckerSkippedForVariantOutcomesWithNonMatchingExperiment(): TestResult = runTest {
        listOf(VariantAudience.Outcome.HOLDOUT, VariantAudience.Outcome.VARIANT_MISS).forEach { outcome ->
            val frequencyChecker: FrequencyChecker = mockk()
            coEvery { frequencyChecker.checkAndIncrement() } returns false
            coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

            val schedule = makeSchedule(
                checker = frequencyChecker,
                info = makeInfo(
                    experimentResult = ExperimentResult(
                        channelId = "channel",
                        contactId = "contact",
                        isMatching = false,
                        allEvaluatedExperimentsMetadata = emptyList()
                    ),
                    variantAudienceResult = VariantAudienceResult(outcome)
                )
            )

            assertEquals(ScheduleReadyResult.READY, executor.isReady(schedule))
            coVerify(exactly = 0) { frequencyChecker.checkAndIncrement() }
        }
    }

    /** A matching global holdout decides the outcome ahead of the variant, so it still charges. */
    @Test
    public fun testFrequencyCheckerChargedForGlobalHoldoutOverVariantOutcome(): TestResult = runTest {
        val frequencyChecker: FrequencyChecker = mockk()
        coEvery { frequencyChecker.checkAndIncrement() } returns true
        coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

        val schedule = makeSchedule(
            checker = frequencyChecker,
            info = makeInfo(
                experimentResult = ExperimentResult(
                    channelId = "channel",
                    contactId = "contact",
                    isMatching = true,
                    allEvaluatedExperimentsMetadata = emptyList()
                ),
                variantAudienceResult = VariantAudienceResult(VariantAudience.Outcome.VARIANT_MISS)
            )
        )

        assertEquals(ScheduleReadyResult.READY, executor.isReady(schedule))
        coVerify(exactly = 1) { frequencyChecker.checkAndIncrement() }
    }

    /** A failed additional audience check is resolved ahead of the variant outcome, so it charges whichever arm the device landed in. */
    @Test
    public fun testFrequencyCheckerChargedForAudienceExcludedOverVariantOutcome(): TestResult = runTest {
        listOf(VariantAudience.Outcome.HOLDOUT, VariantAudience.Outcome.VARIANT_MISS).forEach { outcome ->
            val frequencyChecker: FrequencyChecker = mockk()
            coEvery { frequencyChecker.checkAndIncrement() } returns true
            coEvery { actionExecutor.isReady(any(), any()) } returns ScheduleReadyResult.READY

            val schedule = makeSchedule(
                checker = frequencyChecker,
                info = makeInfo(
                    variantAudienceResult = VariantAudienceResult(outcome),
                    additionalAudienceCheckResult = false
                )
            )

            assertEquals(ScheduleReadyResult.READY, executor.isReady(schedule))
            coVerify(exactly = 1) { frequencyChecker.checkAndIncrement() }
        }
    }

    @Test
    public fun testFrequencyCheckerNotCalledIfNotReady(): TestResult = runTest {
        val frequencyChecker: FrequencyChecker = mockk()
        val schedule = makeSchedule(checker = frequencyChecker)

        val results = listOf(ScheduleReadyResult.NOT_READY, ScheduleReadyResult.INVALIDATE, ScheduleReadyResult.SKIP)

        results.forEach { result ->
            coEvery { actionExecutor.isReady(any(), any()) } returns result
            assertEquals(result, executor.isReady(schedule))
            coVerify(exactly = 0) { frequencyChecker.checkAndIncrement() }
        }
    }

    @Test
    public fun testIsValid(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = "some id",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.NULL),
            created = Instant.ofEpochMilli(0)
        )

        coEvery { remoteDataAccess.isCurrent(eq(schedule)) } returns true

        assertTrue(executor.isValid(schedule))
        coVerify { remoteDataAccess.isCurrent(eq(schedule)) }
    }

    @Test
    public fun testNotValid(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = "some id",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.NULL),
            created = Instant.ofEpochMilli(0)
        )

        coEvery { remoteDataAccess.isCurrent(any()) } returns false

        assertFalse(executor.isValid(schedule))
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
            created = Instant.ofEpochMilli(0)
        )

        val preparedScheduleInfo = PreparedScheduleInfo(scheduleId = "prepared schedule id", triggerSessionId = UUID.randomUUID().toString())

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
            created = Instant.ofEpochMilli(0)
        )

        val preparedScheduleInfo = PreparedScheduleInfo(scheduleId = "prepared schedule id", triggerSessionId = UUID.randomUUID().toString())

        coEvery { messageExecutor.interrupted(any(), any()) } answers {
            assertEquals(schedule, firstArg())
            assertEquals(preparedScheduleInfo, secondArg())
            InterruptedBehavior.FINISH
        }

        assertEquals(InterruptedBehavior.FINISH, executor.interrupted(schedule, preparedScheduleInfo))
        coVerify { messageExecutor.interrupted(eq(schedule), eq(preparedScheduleInfo)) }
    }

    private fun makeInfo(
        experimentResult: ExperimentResult? = null,
        variantAudienceResult: VariantAudienceResult? = null,
        additionalAudienceCheckResult: Boolean = true
    ): PreparedScheduleInfo = PreparedScheduleInfo(
        scheduleId = "schedule id",
        experimentResult = experimentResult,
        variantAudienceResult = variantAudienceResult,
        triggerSessionId = UUID.randomUUID().toString(),
        additionalAudienceCheckResult = additionalAudienceCheckResult
    )

    private fun makeSchedule(
        data: PreparedScheduleData? = null,
        checker: FrequencyChecker? = null,
        info: PreparedScheduleInfo = makeInfo()
    ): PreparedSchedule {
        val scheduleData = data ?: PreparedScheduleData.Action(JsonValue.wrap("neat"))

        return PreparedSchedule(
            info = info,
            data = scheduleData,
            frequencyChecker = checker
        )
    }
}
