package com.urbanairship.iam

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.engine.ScheduleReadyResult
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.adapter.DisplayAdapter
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.iam.assets.AssetCacheManager
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.coordinator.DisplayCoordinator
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
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

    private val displayAdapterReady = MutableStateFlow(true)
    private val displayAdapter: DisplayAdapter = mockk {
        every { isReady } returns displayAdapterReady
    }

    private val displayCoordinatorReady = MutableStateFlow(true)
    private val displayCoordinator: DisplayCoordinator = mockk() {
        every { isReady } returns displayCoordinatorReady
    }
    private val actionRunFactory: ActionRunRequestFactory = mockk()
    private val executor = InAppMessageAutomationExecutor(
        context, assetManager, analyticsFactory, conditionsChangedNotifier, actionRunFactory
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
        analytics = analytics
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
            created = 0u
        )

        every { analytics.recordEvent(any(), any()) } answers {
            val event: InAppEvent = firstArg()
            assertEquals(InAppResolutionEvent.interrupted().name, event.name)
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

        mockActionRunner()

        val result = execute()

        coVerify { displayAdapter.display(any(), any()) }
        verify { displayCoordinator.messageWillDisplay(any()) }
        verify { displayCoordinator.messageFinishedDisplaying(any()) }
        assertEquals(result, ScheduleExecuteResult.FINISHED)
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
            assertEquals(InAppResolutionEvent.control(experimentResult).name, firstArg<InAppEvent>().name)
        }

        assertEquals(execute(info), ScheduleExecuteResult.FINISHED)
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

        mockActionRunner()

        coEvery { assetManager.clearCache(any()) } just runs

        coEvery { displayAdapter.display(any(), any()) } returns DisplayResult.FINISHED
        executor.displayDelegate = delegate

        assertEquals(execute(), ScheduleExecuteResult.FINISHED)

        verify { delegate.messageWillDisplay(any(), any()) }
        verify { delegate.messageFinishedDisplaying(any(), any()) }
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
    }

    @Test
    public fun testAdditionalAudienceCheckMiss(): TestResult = runTest {

        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            throw IllegalArgumentException()
        }

        coEvery { analytics.recordEvent(any(), any()) } answers {
            val event: InAppEvent = firstArg()
            assertEquals(event.name, InAppResolutionEvent.audienceExcluded().name)
        }

        val result = execute(preparedInfo.copy(additionalAudienceCheckResult = false))
        assertEquals(ScheduleExecuteResult.FINISHED, result)

        coVerify { analytics.recordEvent(any(), any()) }
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

        val request = mockActionRunner()

        coEvery { assetManager.clearCache(any()) } just runs

        val result = execute()

        assertEquals(result, ScheduleExecuteResult.CANCEL)
        verify { request.setValue(eq(JsonValue.wrap("payload"))) }
    }

    private fun mockActionRunner(): ActionRunRequest {
        val request: ActionRunRequest = mockk()
        every { actionRunFactory.createActionRequest(any()) } returns request
        every { request.setValue(any<Any>()) } returns request
        every { request.run() } just runs
        return request
    }

    private fun checkReady(): ScheduleReadyResult = executor.isReady(preparedData, preparedInfo)
    private suspend fun execute(info: PreparedScheduleInfo = preparedInfo): ScheduleExecuteResult =
        executor.execute(preparedData, info)
}
