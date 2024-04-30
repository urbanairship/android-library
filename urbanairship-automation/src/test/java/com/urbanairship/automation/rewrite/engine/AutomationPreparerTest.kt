package com.urbanairship.automation.rewrite.engine

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.UAirship
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.rewrite.AutomationAudience
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.deferred.DeferredAutomationData
import com.urbanairship.automation.rewrite.deferred.DeferredScheduleResult
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.PreparedInAppMessageData
import com.urbanairship.automation.rewrite.inappmessage.content.Banner
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonLayoutType
import com.urbanairship.automation.rewrite.limits.FrequencyChecker
import com.urbanairship.automation.rewrite.limits.FrequencyLimitManager
import com.urbanairship.automation.rewrite.remotedata.AutomationRemoteDataAccess
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.experiment.MessageInfo
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Locale
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationPreparerTest {

    private lateinit var preparedMessageData: PreparedInAppMessageData
    private lateinit var preparer: AutomationPreparer

    private val actionPreparer: AutomationPreparerDelegate<JsonValue, JsonValue> = mockk()
    private val messagePreparer: AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData> = mockk()
    private val deferredResolver: DeferredResolver = mockk()

    private val frequencyLimitManager: FrequencyLimitManager = mockk {
        coEvery { getFrequencyChecker(any()) } returns Result.success(null)
    }

    private val audienceSelector: AudienceSelector = mockk()
    private val experimentManager: ExperimentManager = mockk()
    private val remoteDataAccess: AutomationRemoteDataAccess = mockk(relaxed = true)
    private val deviceInfoProvider: DeviceInfoProvider = mockk()
    private val context = ApplicationProvider.getApplicationContext<Context>()


    private val triggerContext = DeferredTriggerContext("some type", 10.0, JsonValue.NULL)

    @Before
    public fun setup() {

        coEvery { deviceInfoProvider.snapshot(any()) } returns deviceInfoProvider

        preparedMessageData = PreparedInAppMessageData(
            message = InAppMessage(
                name = "some name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom")))),
            displayAdapter = mockk(relaxed = true),
            displayCoordinator = mockk(relaxed = true)
        )

        preparer = AutomationPreparer(
            actionPreparer = actionPreparer,
            messagePreparer = messagePreparer,
            deferredResolver = deferredResolver,
            frequencyLimitManager = frequencyLimitManager,
            audienceChecker = audienceSelector,
            experiments = experimentManager,
            remoteDataAccess = remoteDataAccess,
            deviceInfoProvider = deviceInfoProvider
        )
    }

    @Test
    public fun testRequiresUpdate(): TestResult = runTest {
        val schedule = makeSchedule()

        coEvery { remoteDataAccess.requiredUpdate(any()) } answers {
            assertEquals(schedule, firstArg<AutomationSchedule>())
            schedule == firstArg()
        }

        coEvery { remoteDataAccess.waitForFullRefresh(any()) } answers {
            assertEquals(schedule, firstArg<AutomationSchedule>())
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        assertEquals(result, SchedulePrepareResult.Invalidate)

        coVerify { remoteDataAccess.waitForFullRefresh(eq(schedule)) }
    }

    @Test
    public fun testBestEffortRefreshNotCurrent(): TestResult = runTest {
        val schedule = makeSchedule()

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } answers {
            assertEquals(schedule, firstArg())
            false
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        assertEquals(result, SchedulePrepareResult.Invalidate)

        coVerify { remoteDataAccess.bestEffortRefresh(eq(schedule)) }
    }

    @Test
    public fun testFrequencyLimitOverLimit(): TestResult = runTest {
        val constraints = listOf("constraint")


        val frequencyChecker: FrequencyChecker = mockk() {
            every { isOverLimit() } returns true
        }
        coEvery { frequencyLimitManager.getFrequencyChecker(constraints) } returns Result.success(frequencyChecker)

        val schedule = makeSchedule(constraints = constraints)

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        assertEquals(SchedulePrepareResult.Skip, preparer.prepare(context, schedule, triggerContext))

        verify { frequencyChecker.isOverLimit() }
        coVerify { frequencyLimitManager.getFrequencyChecker(constraints) }
    }

    @Test
    public fun testAudienceMismatchSkip(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.SKIP
            )
        )

        coEvery { remoteDataAccess.contactIDFor(any()) } answers {
            assertEquals(schedule, firstArg())
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } answers {
            val args = args
            assertEquals(0L, args[1])
            assertNull(args[3])
            false
        }

        assertEquals(SchedulePrepareResult.Skip, preparer.prepare(context, schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any(), any(), any()) }
    }

    @Test
    public fun testAudienceMismatchPenalize(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            )
        )

        coEvery { remoteDataAccess.contactIDFor(any()) } answers {
            assertEquals(schedule, firstArg())
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } answers {
            val args = args
            assertEquals(0L, args[1])
            assertNull(args[3])
            false
        }

        assertEquals(SchedulePrepareResult.Penalize, preparer.prepare(context, schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any(), any(), any()) }
    }

    @Test
    public fun testAudienceMismatchCancel(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.CANCEL
            )
        )

        coEvery { remoteDataAccess.contactIDFor(any()) } answers {
            assertEquals(schedule, firstArg())
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } answers {
            val args = args
            assertEquals(0L, args[1])
            assertNull(args[3])
            false
        }

        assertEquals(SchedulePrepareResult.Cancel, preparer.prepare(context, schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any(), any(), any()) }
    }

    @Test
    public fun testContactIDAudienceChecks(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            )
        )

        coEvery { remoteDataAccess.contactIDFor(any()) } answers {
            assertEquals(schedule, firstArg())
            "contact id"
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } answers {
            val args = args
            assertEquals("contact id", args[3])
            false
        }

        assertEquals(SchedulePrepareResult.Penalize, preparer.prepare(context, schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any(), any(), any()) }
    }

    @Test
    public fun testPrepareMessage(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { remoteDataAccess.contactIDFor(any()) } returns "contact id"
        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true

        mockExperimentsManager()

        coEvery { messagePreparer.prepare(any(), any()) } answers  {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(firstArg()))
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactID)
            return@answers preparedMessageData
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleID)
            assertEquals(schedule.campaigns, result.schedule.info.campaigns)
            assertEquals(result.schedule.data, PreparedScheduleData.InAppMessage(preparedMessageData))
        } else {
            fail()
        }

        coVerify { messagePreparer.prepare(any(), any()) }
    }

    @Test
    public fun testPrepareInvalidMessage(): TestResult = runTest {
        val invalidBanner = InAppMessageDisplayContent.BannerContent(
            Banner(
                heading = null,
                body = null,
                media = null,
                buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
                template = Banner.Template.MEDIA_LEFT,
                borderRadius = 5F,
                duration = 100L,
                placement = Banner.Placement.TOP
            )
        )

        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            displayContent = invalidBanner
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"

        mockExperimentsManager()

        coEvery { messagePreparer.prepare(any(), any()) } answers  {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(firstArg()))
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactID)
            return@answers preparedMessageData
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        assertEquals(SchedulePrepareResult.Skip, result)
        coVerify { messagePreparer.prepare(any(), any()) }
    }

    @Test
    public fun testPrepareActions(): TestResult = runTest {
        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("action payload")),
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"

        mockExperimentsManager()

        coEvery { actionPreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.data, AutomationSchedule.ScheduleData.Actions(firstArg()))
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactID)

            return@answers firstArg()
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleID)
            assertEquals(schedule.campaigns, result.schedule.info.campaigns)
            assertEquals(result.schedule.data, PreparedScheduleData.Action(JsonValue.wrap("action payload")))
        } else {
            fail()
        }

        coVerify { actionPreparer.prepare(any(), any()) }
    }

    @Test
    public fun testPrepareDeferredActions(): TestResult = runTest {
        val actions = jsonMapOf("some" to "action").toJsonValue()

        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Deferred(
                DeferredAutomationData(
                    url = Uri.parse("https://sample.url"),
                    retryOnTimeOut = false,
                    type = DeferredAutomationData.DeferredType.ACTIONS
                )
            ),
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        mockExperimentsManager()

        coEvery { deferredResolver.resolve<DeferredScheduleResult>(any(), any()) } answers {
            val request: DeferredRequest = firstArg()

            assertEquals(request.uri, Uri.parse("https://sample.url"))
            assertEquals(request.channelID, "channel-id")
            assertNull(request.contactID) //TODO: is that correct. do we ignore contact id for deferred?
            assertEquals(request.triggerContext, triggerContext)

            return@answers DeferredResult.Success(
                DeferredScheduleResult(
                    isAudienceMatch = true,
                    actions = actions,
                )
            )
        }

        coEvery { actionPreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(actions, firstArg())
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactID)

            return@answers firstArg()
        }

        mockkStatic(UAirship::class)
        every { UAirship.getAppVersion() } returns 123
        every { UAirship.getVersion() } returns "1"

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleID)
            assertEquals(schedule.campaigns, result.schedule.info.campaigns)
            assertEquals(result.schedule.data, PreparedScheduleData.Action(actions))
        } else {
            fail()
        }

        coVerify { actionPreparer.prepare(any(), any()) }
    }

    @Test
    public fun testPrepareDeferredMessage(): TestResult = runTest {
        val message = InAppMessage(
            name = "some name",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("content"))),
            source = InAppMessage.InAppMessageSource.REMOTE_DATA
        )

        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Deferred(
                DeferredAutomationData(
                    url = Uri.parse("https://sample.url"),
                    retryOnTimeOut = false,
                    type = DeferredAutomationData.DeferredType.IN_APP_MESSAGE
                )
            ),
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        mockExperimentsManager()

        coEvery { deferredResolver.resolve<DeferredScheduleResult>(any(), any()) } answers {
            val request: DeferredRequest = firstArg()

            assertEquals(request.uri, Uri.parse("https://sample.url"))
            assertEquals(request.channelID, "channel-id")
            assertNull(request.contactID) //TODO: is that correct. do we ignore contact id for deferred?
            assertEquals(request.triggerContext, triggerContext)

            return@answers DeferredResult.Success(
                DeferredScheduleResult(
                    isAudienceMatch = true,
                    message = message
                )
            )
        }

        preparedMessageData = PreparedInAppMessageData(
            message = InAppMessage(
                name = "some name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("content"))),
                source = InAppMessage.InAppMessageSource.REMOTE_DATA),
            displayAdapter = mockk(relaxed = true),
            displayCoordinator = mockk(relaxed = true)
        )

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(message, firstArg())
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactID)

            return@answers preparedMessageData
        }

        mockkStatic(UAirship::class)
        every { UAirship.getAppVersion() } returns 123
        every { UAirship.getVersion() } returns "1"

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleID)
            assertEquals(schedule.campaigns, result.schedule.info.campaigns)
            if (result.schedule.data is PreparedScheduleData.InAppMessage) {
                assertEquals(result.schedule.data.message.message, message)
            } else {
                fail()
            }
        } else {
            fail()
        }

        coVerify { messagePreparer.prepare(any(), any()) }
    }

    @Test
    public fun testPrepareDeferredAudienceMismatchResult(): TestResult = runTest {
        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Deferred(
                DeferredAutomationData(
                    url = Uri.parse("https://sample.url"),
                    retryOnTimeOut = false,
                    type = DeferredAutomationData.DeferredType.IN_APP_MESSAGE
                )
            ),
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.SKIP
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        mockExperimentsManager()

        coEvery { deferredResolver.resolve<DeferredScheduleResult>(any(), any()) } answers {
            return@answers DeferredResult.Success(
                DeferredScheduleResult(
                    isAudienceMatch = false
                )
            )
        }

        mockkStatic(UAirship::class)
        every { UAirship.getAppVersion() } returns 123
        every { UAirship.getVersion() } returns "1"

        assertEquals(SchedulePrepareResult.Skip, preparer.prepare(context, schedule, triggerContext))
    }

    @Test
    public fun testExperiments(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            messageType = "some message type"
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        val experimentResult = ExperimentResult(
            channelId = "channel-id",
            contactId = "contact id",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(jsonMapOf("some" to "reporting"))
        )

        coEvery { experimentManager.evaluateExperiments(any(), any()) } answers {
            val messageInfo: MessageInfo = firstArg()
            assertEquals(messageInfo, MessageInfo(
                messageType = schedule.messageType!!,
                campaigns = schedule.campaigns
            ))
            assertEquals("contact id", secondArg())

            experimentResult
        }

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(info.experimentResult, experimentResult)

            return@answers preparedMessageData
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(result.schedule.info.experimentResult, experimentResult)
        } else {
            fail()
        }
    }

    @Test
    public fun testExperimentsDefaultMessageType(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        val experimentResult = ExperimentResult(
            channelId = "channel-id",
            contactId = "contact id",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(jsonMapOf("some" to "reporting"))
        )

        coEvery { experimentManager.evaluateExperiments(any(), any()) } answers {
            val messageInfo: MessageInfo = firstArg()
            assertEquals(messageInfo, MessageInfo(
                messageType = "transactional",
                campaigns = schedule.campaigns
            ))
            assertEquals("contact id", secondArg())

            experimentResult
        }

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleID)
            assertEquals(info.experimentResult, experimentResult)

            return@answers preparedMessageData
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(result.schedule.info.experimentResult, experimentResult)
        } else {
            fail()
        }
    }

    @Test
    public fun testByPassExperiments(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            messageType = "some message type",
            bypassHoldoutGroup = true
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        coEvery { experimentManager.evaluateExperiments(any(), any()) } answers {
            fail()
            null
        }

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleID)
            assertNull(info.experimentResult)

            return@answers preparedMessageData
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertNull(result.schedule.info.experimentResult)
        } else {
            fail()
        }
    }

    @Test
    public fun testByPassExperimentsActions(): TestResult = runTest {
        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("action")),
            audience = AutomationAudience(
                audienceSelector = AudienceSelector.newBuilder().build(),
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            messageType = "some message type",
            bypassHoldoutGroup = true
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any(), any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactId() } returns "contact id"
        coEvery { deviceInfoProvider.channelId } returns "channel-id"
        coEvery { deviceInfoProvider.getUserLocale(any()) } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true


        coEvery { experimentManager.evaluateExperiments(any(), any()) } answers {
            fail()
            null
        }

        coEvery { actionPreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleID)
            assertNull(info.experimentResult)

            return@answers firstArg()
        }

        val result = preparer.prepare(context, schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertNull(result.schedule.info.experimentResult)
        } else {
            fail()
        }
    }

    private fun makeSchedule(
        constraints: List<String>? = null,
        audience: AutomationAudience? = null,
        campaigns: JsonValue? = null,
        displayContent: InAppMessageDisplayContent? = null,
        data: AutomationSchedule.ScheduleData? = null,
        messageType: String? = null,
        bypassHoldoutGroup: Boolean? = null
    ): AutomationSchedule {
        val content = displayContent ?: InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL))
        val scheduleData = data ?: AutomationSchedule.ScheduleData.InAppMessageData(InAppMessage(
            name = "message",
            displayContent = content
        ))

        return AutomationSchedule(
            identifier = "test-schedule",
            triggers = listOf(),
            data = scheduleData,
            created = 0U,
            frequencyConstraintIds = constraints,
            audience = audience,
            campaigns = campaigns,
            messageType = messageType,
            bypassHoldoutGroups = bypassHoldoutGroup
        )
    }


    private fun mockExperimentsManager() {
        coEvery { experimentManager.evaluateExperiments(any(), any()) } returns null
    }
}
