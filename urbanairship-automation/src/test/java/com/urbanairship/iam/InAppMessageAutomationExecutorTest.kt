package com.urbanairship.iam

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.android.layout.analytics.DisplayResult
import com.urbanairship.audience.VariantAudience
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.engine.ScheduleReadyResult
import com.urbanairship.automation.engine.VariantAudienceResult
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.limits.TestAutomationLedger
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.actions.InAppActionRunner
import com.urbanairship.iam.adapter.DisplayAdapter
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.analytics.events.LayoutResolutionEvent
import com.urbanairship.android.layout.assets.AssetCacheManager
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.coordinator.DisplayCoordinator
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.time.Instant
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageAutomationExecutorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val assetManager: AssetCacheManager = mockk()
    private val analytics: InAppMessageAnalyticsInterface = mockk()
    private val analyticsFactory: InAppMessageAnalyticsFactory = mockk()
    private val conditionsChangedNotifier = ScheduleConditionsChangedNotifier()
    private val actionRunner: InAppActionRunner = mockk()
    private val ledger = TestAutomationLedger()

    private val displayAdapterReady = MutableStateFlow(true)
    private val displayAdapter: DisplayAdapter = mockk {
        every { isReady } returns displayAdapterReady
    }

    private val displayCoordinatorReady = MutableStateFlow(true)
    private val displayCoordinator: DisplayCoordinator = mockk() {
        every { isReady } returns displayCoordinatorReady
    }
    private val executor = InAppMessageAutomationExecutor(
        context, assetManager, analyticsFactory, conditionsChangedNotifier, ledger
    )

    private val preparedInfo = PreparedScheduleInfo(
        scheduleId = UUID.randomUUID().toString(),
        productId = UUID.randomUUID().toString(),
        campaigns = JsonValue.wrap(UUID.randomUUID().toString()),
        contactId = UUID.randomUUID().toString(),
        reportingContext = JsonValue.wrap(UUID.randomUUID().toString()),
        triggerSessionId = UUID.randomUUID().toString()
    )

    private val preparedData = PreparedInAppMessageData(
        message = InAppMessage(
            name = "test",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL)),
            actions = jsonMapOf("action" to "payload")
        ),
        displayAdapter = displayAdapter,
        displayCoordinator = displayCoordinator,
        analytics = analytics,
        actionRunner = actionRunner,
    )

    @Before
    public fun setup() {
        coEvery { analyticsFactory.makeAnalytics(any(), any()) } returns analytics
    }

    @Test
    public fun testIsReady(): TestResult = runTest {
        displayAdapterReady.value = true
        displayCoordinatorReady.value = true

        assertEquals(checkReady(), ScheduleReadyResult.READY)
    }

    @Test
    public fun testNotReadyAdapter(): TestResult = runTest {
        displayAdapterReady.value = false
        displayCoordinatorReady.value = true

        assertEquals(checkReady(), ScheduleReadyResult.NOT_READY)
    }

    @Test
    public fun testNotReadyCoordinator(): TestResult = runTest {
        displayAdapterReady.value = true
        displayCoordinatorReady.value = false

        assertEquals(checkReady(), ScheduleReadyResult.NOT_READY)
    }

    @Test
    public fun testIsReadyDelegate(): TestResult = runTest {
        displayAdapterReady.value = true
        displayCoordinatorReady.value = true

        val delegate: InAppMessageDisplayDelegate = mockk()
        executor.displayDelegate = delegate
        var isDelegateReady = true
        every { delegate.isMessageReadyToDisplay(any(), any()) } answers {
            assertEquals(preparedData.message, firstArg())
            assertEquals(preparedInfo.scheduleId, secondArg())
            isDelegateReady
        }

        assertEquals(checkReady(), ScheduleReadyResult.READY)

        isDelegateReady = false
        assertEquals(checkReady(), ScheduleReadyResult.NOT_READY)
    }

    @Test
    public fun testInterrupted(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = preparedInfo.scheduleId,
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.InAppMessageData(preparedData.message),
            created = Instant.ofEpochMilli(0),
        )

        every { analytics.recordEvent(any(), any()) } answers {
            val event: LayoutEvent = firstArg()
            assertEquals(LayoutResolutionEvent.interrupted().eventType, event.eventType)
        }

        coEvery { assetManager.clearCache(any()) } answers {
            assertEquals(preparedInfo.scheduleId, firstArg())
        }

        executor.interrupted(schedule, preparedInfo)

        verify { analytics.recordEvent(any(), any()) }
        coVerify { assetManager.clearCache(any()) }
    }

    @Test
    public fun testExecute(): TestResult = runTest {

        every { displayCoordinator.messageWillDisplay(any()) } answers {
            assertEquals(preparedData.message, firstArg())
        }

        every { displayCoordinator.messageFinishedDisplaying(any()) } answers {
            assertEquals(preparedData.message, firstArg())
        }

        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            assertEquals(context, firstArg())
            assertEquals(analytics, secondArg())
            DisplayResult.FINISHED
        }

        coEvery { assetManager.clearCache(any()) } just runs

        coEvery { actionRunner.run(any(), any(), Action.Situation.AUTOMATION) } just runs

        val result = execute()

        coVerify { displayAdapter.display(any(), any()) }
        verify { displayCoordinator.messageWillDisplay(any()) }
        verify { displayCoordinator.messageFinishedDisplaying(any()) }
        assertEquals(result, ScheduleExecuteResult.FINISHED)

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = preparedInfo.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.SUCCEEDED,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testExecuteInControlGroup(): TestResult = runTest {
        val experimentResult = ExperimentResult(
            channelId = "some channel",
            contactId = "some contact",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf()
        )

        val info = PreparedScheduleInfo(
            scheduleId = preparedInfo.scheduleId,
            productId = preparedInfo.productId,
            campaigns = preparedInfo.campaigns,
            contactId = preparedInfo.contactId,
            reportingContext = preparedInfo.reportingContext,
            experimentResult = experimentResult,
            triggerSessionId = UUID.randomUUID().toString()
        )

        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        coEvery { assetManager.clearCache(any()) } just runs

        every { analytics.recordEvent(any(), any()) } answers {
            assertEquals(LayoutResolutionEvent.control(experimentResult).eventType, firstArg<LayoutEvent>().eventType)
        }

        assertEquals(execute(info), ScheduleExecuteResult.FINISHED)

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = info.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.HOLDOUT,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testExecuteGlobalHoldoutTakesPrecedenceOverVariantHoldout(): TestResult = runTest {
        // A schedule can independently resolve to both the global holdout mechanism and its
        // own variant experiment's holdout arm at the same last-mile point. Only one outcome
        // may be reported: the global holdout event, recorded once.
        val experimentResult = ExperimentResult(
            channelId = "some channel",
            contactId = "some contact",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf()
        )

        val info = preparedInfo.copy(
            experimentResult = experimentResult,
            variantAudienceResult = VariantAudienceResult(outcome = VariantAudience.Outcome.HOLDOUT)
        )

        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        coEvery { assetManager.clearCache(any()) } just runs

        every { analytics.recordEvent(any(), any()) } answers {
            assertEquals(LayoutResolutionEvent.control(experimentResult).eventType, firstArg<LayoutEvent>().eventType)
        }

        assertEquals(execute(info), ScheduleExecuteResult.FINISHED)

        verify(exactly = 1) { analytics.recordEvent(any(), any()) }

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = info.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.HOLDOUT,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testExecuteVariantHoldout(): TestResult = runTest {
        val info = preparedInfo.copy(
            variantAudienceResult = VariantAudienceResult(outcome = VariantAudience.Outcome.HOLDOUT)
        )

        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        coEvery { assetManager.clearCache(any()) } just runs

        every { analytics.recordEvent(any(), any()) } answers {
            assertEquals(LayoutResolutionEvent.variantControl().eventType, firstArg<LayoutEvent>().eventType)
        }

        assertEquals(execute(info), ScheduleExecuteResult.FINISHED)

        // A variant experiment's own holdout arm is indistinguishable from the global holdout
        // mechanism in ledger terms, even though it reports its own resolution event.
        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = info.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.HOLDOUT,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testExecuteVariantMiss(): TestResult = runTest {
        val info = preparedInfo.copy(
            variantAudienceResult = VariantAudienceResult(outcome = VariantAudience.Outcome.VARIANT_MISS)
        )

        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        coEvery { assetManager.clearCache(any()) } just runs

        every { analytics.recordEvent(any(), any()) } answers {
            assertEquals(LayoutResolutionEvent.variantMiss().eventType, firstArg<LayoutEvent>().eventType)
        }

        assertEquals(execute(info), ScheduleExecuteResult.FINISHED)

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = info.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.VARIANT_MISS,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testExecuteVariantMatchedDisplaysNormally(): TestResult = runTest {
        val info = preparedInfo.copy(
            variantAudienceResult = VariantAudienceResult(outcome = VariantAudience.Outcome.MATCHED)
        )

        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        coEvery { assetManager.clearCache(any()) } just runs
        coEvery { actionRunner.run(any(), any(), Action.Situation.AUTOMATION) } just runs

        coEvery { displayAdapter.display(any(), any()) } returns DisplayResult.FINISHED

        assertEquals(execute(info), ScheduleExecuteResult.FINISHED)
        coVerify { displayAdapter.display(any(), any()) }

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = info.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.SUCCEEDED,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testExecuteDisplayAdapter(): TestResult = runTest {
        val delegate: InAppMessageDisplayDelegate = mockk()
        every { delegate.messageWillDisplay(any(), any()) } answers {
            assertEquals(preparedData.message, firstArg())
            assertEquals(preparedInfo.scheduleId, secondArg())
        }
        every { delegate.messageFinishedDisplaying(any(), any()) } answers {
            assertEquals(preparedData.message, firstArg())
            assertEquals(preparedInfo.scheduleId, secondArg())
        }

        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        every { analytics.recordEvent(any(), any()) } just runs

        coEvery { actionRunner.run(any(), any(), Action.Situation.AUTOMATION) } just runs

        coEvery { assetManager.clearCache(any()) } just runs

        coEvery { displayAdapter.display(any(), any()) } returns DisplayResult.FINISHED
        executor.displayDelegate = delegate

        assertEquals(execute(), ScheduleExecuteResult.FINISHED)

        verify { delegate.messageWillDisplay(any(), any()) }
        verify { delegate.messageFinishedDisplaying(any(), any()) }
        verify { actionRunner.run(any(), any(), Action.Situation.AUTOMATION) }
    }

    @Test
    public fun testExecuteDisplayException(): TestResult = runTest {
        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs
        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            throw IllegalArgumentException()
        }

        coEvery { assetManager.clearCache(any()) } just runs

        val result = execute()

        assertEquals(result, ScheduleExecuteResult.RETRY)
        // A failed display never reaches the success recording.
        assertTrue(ledger.recorded.isEmpty())
    }

    @Test
    public fun testAdditionalAudienceCheckMiss(): TestResult = runTest {

        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            throw IllegalArgumentException()
        }

        coEvery { analytics.recordEvent(any(), any()) } answers {
            val event: LayoutEvent = firstArg()
            assertEquals(event.eventType, LayoutResolutionEvent.audienceExcluded().eventType)
        }

        val result = execute(preparedInfo.copy(additionalAudienceCheckResult = false))
        assertEquals(ScheduleExecuteResult.FINISHED, result)

        coVerify { analytics.recordEvent(any(), any()) }
        // An additional-audience miss is not a budget-consuming execution.
        assertTrue(ledger.recorded.isEmpty())
    }

    @Test
    public fun testExecuteCancel(): TestResult = runTest {
        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs

        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            assertEquals(context, firstArg())
            assertEquals(analytics, secondArg())
            DisplayResult.CANCEL
        }

        coEvery { actionRunner.run(any(), any(), Action.Situation.AUTOMATION) } just runs

        coEvery { assetManager.clearCache(any()) } just runs

        val result = execute()

        assertEquals(result, ScheduleExecuteResult.CANCEL)
        verify { actionRunner.run(any(), any(), Action.Situation.AUTOMATION) }

        // A cancelled display still displayed, so it records a success.
        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = preparedInfo.scheduleId,
                    sharedId = null,
                    triggerId = null,
                    result = LedgerExecutionResult.SUCCEEDED,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    private fun checkReady(): ScheduleReadyResult = executor.isReady(preparedData, preparedInfo)
    private suspend fun execute(info: PreparedScheduleInfo = preparedInfo): ScheduleExecuteResult =
        executor.execute(preparedData, info)
}
