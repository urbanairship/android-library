package com.urbanairship.automation.rewrite.inappmessage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.ScheduleExecuteResult
import com.urbanairship.automation.rewrite.ScheduleReadyResult
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppResolutionEvent
import com.urbanairship.automation.rewrite.inappmessage.assets.AssetCacheManagerInterface
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinator
import com.urbanairship.automation.rewrite.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonValue
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
    private val assetManager: AssetCacheManagerInterface = mockk()
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
        scheduleID = UUID.randomUUID().toString(),
        productID = UUID.randomUUID().toString(),
        campaigns = JsonValue.wrap(UUID.randomUUID().toString()),
        contactID = UUID.randomUUID().toString(),
        reportingContext = JsonValue.wrap(UUID.randomUUID().toString())
    )

    private val preparedData = PreparedInAppMessageData(
        message = InAppMessage(
            name = "test",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL)),
            actions = mapOf("action" to JsonValue.wrap("payload"))
        ),
        displayAdapter = displayAdapter,
        displayCoordinator = displayCoordinator
    )

    @Before
    public fun setup() {
        every { analyticsFactory.makeAnalytics(any(), any()) } returns analytics

        every { analyticsFactory.makeAnalytics(any(), any(), any(), any(), any(), any(), any()) } answers {
            assertEquals(preparedInfo.scheduleID, args[0])
            assertEquals(preparedInfo.productID, args[1])
            assertEquals(preparedInfo.contactID, args[2])
            assertEquals(preparedData.message, args[3])
            assertEquals(preparedInfo.campaigns, args[4])
            assertEquals(preparedInfo.reportingContext, args[5])
            analytics
        }
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
            assertEquals(preparedInfo.scheduleID, secondArg())
            isDelegateReady
        }

        assertEquals(checkReady(), ScheduleReadyResult.READY)

        isDelegateReady = false
        assertEquals(checkReady(), ScheduleReadyResult.NOT_READY)
    }

    @Test
    public fun testInterrupted(): TestResult = runTest {
        val schedule = AutomationSchedule(
            identifier = preparedInfo.scheduleID,
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.InAppMessageData(preparedData.message),
            created = 0u
        )

        every { analytics.recordEvent(any(), any()) } answers {
            val event: InAppEvent = firstArg()
            assertEquals(InAppResolutionEvent.interrupted().name, event.name)
        }

        coEvery { assetManager.clearCache(any()) } answers {
            assertEquals(preparedInfo.scheduleID, firstArg())
        }

        executor.interrupted(schedule, preparedInfo)

        verify { analytics.recordEvent(any(), any()) }
        coVerify { assetManager.clearCache(any()) }
    }

    @Test
    public fun testExecute(): TestResult = runTest {

        coEvery { analytics.recordImpression() } just runs
        every { displayCoordinator.messageWillDisplay(any()) } answers {
            assertEquals(preparedData.message, firstArg())
        }

        every { displayCoordinator.messageFinishedDisplaying(any()) } answers {
            assertEquals(preparedData.message, firstArg())
        }

        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            assertEquals(context, firstArg())
            assertEquals(analytics, secondArg())
            analytics.recordImpression()
            DisplayResult.FINISHED
        }

        coEvery { assetManager.clearCache(any()) } just runs

        mockActionRunner()

        val result = execute()

        coVerify(exactly = 1) { analytics.recordImpression() }
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
            scheduleID = preparedInfo.scheduleID,
            productID = preparedInfo.productID,
            campaigns = preparedInfo.campaigns,
            contactID = preparedInfo.contactID,
            reportingContext = preparedInfo.reportingContext,
            experimentResult = experimentResult
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
            assertEquals(preparedInfo.scheduleID, secondArg())
        }
        every { delegate.messageFinishedDisplaying(any(), any()) } answers {
            assertEquals(preparedData.message, firstArg())
            assertEquals(preparedInfo.scheduleID, secondArg())
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
    public fun testExecuteCancel(): TestResult = runTest {
        every { displayCoordinator.messageWillDisplay(any()) } just runs
        every { displayCoordinator.messageFinishedDisplaying(any()) } just runs

        coEvery { analytics.recordImpression() } just runs

        coEvery { displayAdapter.display(any(), any()) } coAnswers {
            assertEquals(context, firstArg())
            assertEquals(analytics, secondArg())
            analytics.recordImpression()
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

    private suspend fun checkReady(): ScheduleReadyResult = executor.isReady(preparedData, preparedInfo)
    private suspend fun execute(info: PreparedScheduleInfo = preparedInfo): ScheduleExecuteResult =
        executor.execute(preparedData, info)
}
