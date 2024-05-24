package com.urbanairship.automation.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.UAirship
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.Event
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.audiencecheck.AdditionalAudienceCheckerResolver
import com.urbanairship.automation.deferred.DeferredAutomationData
import com.urbanairship.automation.deferred.DeferredScheduleResult
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.contacts.StableContactInfo
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.experiment.MessageInfo
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
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
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
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
    private val deviceInfoProvider: DeviceInfoProvider = mockk {
        every { appVersionName } returns "test-1.2.3"
        coEvery { getChannelId() } returns "channel-id"
    }

    private val triggerContext = DeferredTriggerContext("some type", 10.0, JsonValue.NULL)
    private val audienceResolver: AdditionalAudienceCheckerResolver = mockk()
    private val analytics: Analytics = mockk()

    @Before
    public fun setup() {

        preparedMessageData = PreparedInAppMessageData(
            message = InAppMessage(
                name = "some name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("custom")))),
            displayAdapter = mockk(relaxed = true),
            displayCoordinator = mockk(relaxed = true)
        )

        coEvery { audienceResolver.resolve(any(), any()) } returns Result.success(true)

        preparer = AutomationPreparer(
            actionPreparer = actionPreparer,
            messagePreparer = messagePreparer,
            deferredResolver = deferredResolver,
            frequencyLimitManager = frequencyLimitManager,
            experiments = experimentManager,
            remoteDataAccess = remoteDataAccess,
            deviceInfoProviderFactory = { deviceInfoProvider },
            additionalAudienceResolver = audienceResolver
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

        val result = preparer.prepare(schedule, triggerContext)
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

        val result = preparer.prepare(schedule, triggerContext)
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

        assertEquals(SchedulePrepareResult.Skip, preparer.prepare(schedule, triggerContext))

        verify { frequencyChecker.isOverLimit() }
        coVerify { frequencyLimitManager.getFrequencyChecker(constraints) }
    }

    @Test
    public fun testAudienceMismatchSkip(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.SKIP
            )
        )

        coEvery { remoteDataAccess.contactIdFor(any()) } answers {
            assertEquals(schedule, firstArg())
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any()) } answers {
            val args = args
            assertEquals(0L, args[0])
            false
        }

        assertEquals(SchedulePrepareResult.Skip, preparer.prepare(schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any()) }
    }

    @Test
    public fun testAudienceMismatchPenalize(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            )
        )

        coEvery { remoteDataAccess.contactIdFor(any()) } answers {
            assertEquals(schedule, firstArg())
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any()) } answers {
            val args = args
            assertEquals(0L, args[0])
            false
        }

        assertEquals(SchedulePrepareResult.Penalize, preparer.prepare(schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any()) }
    }

    @Test
    public fun testAdditionalAudienceMiss(): TestResult = runTest {
        val schedule = makeSchedule()

        coEvery { remoteDataAccess.contactIdFor(any()) } answers {
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any()) } answers {
            true
        }

        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("stable contact id", null)

        every { analytics.addEvent(any()) } answers {
            val event: Event = firstArg()
            assertEquals(event.type, "audience_check_excluded")
            true
        }

        mockExperimentsManager()

        coEvery { audienceResolver.resolve(any(), any()) } coAnswers {
            val info: DeviceInfoProvider = firstArg()
            assertEquals("channel-id", info.getChannelId())
            assertEquals(null, args[1])
            Result.success(false)
        }

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertFalse(info.additionalAudienceCheckResult)
            return@answers Result.success(preparedMessageData)
        }

        val preparedResult = preparer.prepare(schedule, triggerContext) as? SchedulePrepareResult.Prepared
        assertNotNull(preparedResult)
        assertTrue(preparedResult?.schedule?.info?.additionalAudienceCheckResult == false)

        coVerify { audienceResolver.resolve(any(), any()) }
    }

    @Test
    public fun testAudienceMismatchCancel(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.CANCEL
            )
        )

        coEvery { remoteDataAccess.contactIdFor(any()) } answers {
            assertEquals(schedule, firstArg())
            null
        }

        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true

        coEvery { audienceSelector.evaluate(any(), any()) } answers {
            val args = args
            assertEquals(0L, args[0])
            false
        }

        assertEquals(SchedulePrepareResult.Cancel, preparer.prepare(schedule, triggerContext))

        coVerify { audienceSelector.evaluate(any(), any()) }
    }

    @Test
    public fun testPrepareMessage(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { remoteDataAccess.contactIdFor(any()) } returns "contact id"
        coEvery { remoteDataAccess.requiredUpdate(any()) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(any()) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true

        mockExperimentsManager()

        coEvery { messagePreparer.prepare(any(), any()) } answers  {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(firstArg()))
            assertEquals(schedule.identifier, info.scheduleId)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactId)
            return@answers Result.success(preparedMessageData)
        }

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleId)
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
                durationMs = 100L,
                placement = Banner.Placement.TOP
            )
        )

        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            displayContent = invalidBanner
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)

        mockExperimentsManager()

        val result = preparer.prepare(schedule, triggerContext)
        assertEquals(SchedulePrepareResult.Skip, result)
        coVerify(exactly = 0) { messagePreparer.prepare(any(), any()) }
    }

    @Test
    public fun testPrepareActions(): TestResult = runTest {
        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("action payload")),
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)

        mockExperimentsManager()

        coEvery { actionPreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.data, AutomationSchedule.ScheduleData.Actions(firstArg()))
            assertEquals(schedule.identifier, info.scheduleId)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactId)

            return@answers Result.success(firstArg())
        }

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleId)
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
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        mockExperimentsManager()

        coEvery { deferredResolver.resolve<DeferredScheduleResult>(any(), any()) } answers {
            val request: DeferredRequest = firstArg()

            assertEquals(request.uri, Uri.parse("https://sample.url"))
            assertEquals(request.channelId, "channel-id")
            assertNull(request.contactId) //TODO: is that correct. do we ignore contact id for deferred?
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
            assertEquals(schedule.identifier, info.scheduleId)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactId)

            return@answers Result.success(firstArg())
        }


        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleId)
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
            source =  InAppMessage.Source.REMOTE_DATA
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
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        mockExperimentsManager()

        coEvery { deferredResolver.resolve<DeferredScheduleResult>(any(), any()) } answers {
            val request: DeferredRequest = firstArg()

            assertEquals(request.uri, Uri.parse("https://sample.url"))
            assertEquals(request.channelId, "channel-id")
            assertNull(request.contactId) //TODO: is that correct. do we ignore contact id for deferred?
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
                source =  InAppMessage.Source.REMOTE_DATA),
            displayAdapter = mockk(relaxed = true),
            displayCoordinator = mockk(relaxed = true)
        )

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(message, firstArg())
            assertEquals(schedule.identifier, info.scheduleId)
            assertEquals(schedule.campaigns, info.campaigns)
            assertEquals("contact id", info.contactId)

            return@answers Result.success(preparedMessageData)
        }

        mockkStatic(UAirship::class)
        every { UAirship.getAppVersion() } returns 123
        every { UAirship.getVersion() } returns "1"

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(schedule.identifier, result.schedule.info.scheduleId)
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
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.SKIP
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint")
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
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

        assertEquals(SchedulePrepareResult.Skip, preparer.prepare(schedule, triggerContext))
    }

    @Test
    public fun testExperiments(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            messageType = "some message type"
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        val experimentResult = ExperimentResult(
            channelId = "channel-id",
            contactId = "contact id",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(jsonMapOf("some" to "reporting"))
        )

        coEvery { experimentManager.evaluateExperiments(any(), any()) } returns Result.success(experimentResult)

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleId)
            assertEquals(info.experimentResult, experimentResult)

            return@answers Result.success(preparedMessageData)
        }

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(result.schedule.info.experimentResult, experimentResult)
        } else {
            fail()
        }

        coVerify {
            experimentManager.evaluateExperiments(
                MessageInfo(
                    messageType = schedule.messageType!!,
                    campaigns = schedule.campaigns
                ),
                deviceInfoProvider
            )
        }
    }

    @Test
    public fun testExperimentsDefaultMessageType(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        val experimentResult = ExperimentResult(
            channelId = "channel-id",
            contactId = "contact id",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(jsonMapOf("some" to "reporting"))
        )

        coEvery { experimentManager.evaluateExperiments(any(), any()) } returns Result.success(experimentResult)

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleId)
            assertEquals(info.experimentResult, experimentResult)

            return@answers Result.success(preparedMessageData)
        }

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertEquals(result.schedule.info.experimentResult, experimentResult)
        } else {
            fail()
        }

        coVerify {
            experimentManager.evaluateExperiments(
                MessageInfo(
                    messageType = "transactional",
                    campaigns = schedule.campaigns
                ),
                deviceInfoProvider
            )
        }
    }

    @Test
    public fun testByPassExperiments(): TestResult = runTest {
        val schedule = makeSchedule(
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            messageType = "some message type",
            bypassHoldoutGroup = true
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        coEvery { messagePreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleId)
            assertNull(info.experimentResult)

            return@answers Result.success(preparedMessageData)
        }

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertNull(result.schedule.info.experimentResult)
        } else {
            fail()
        }

        coVerify(exactly = 0) { experimentManager.evaluateExperiments(any(), any()) }

    }

    @Test
    public fun testByPassExperimentsActions(): TestResult = runTest {
        val schedule = makeSchedule(
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("action")),
            audience = AutomationAudience(
                audienceSelector = audienceSelector,
                missBehavior = AutomationAudience.MissBehavior.PENALIZE
            ),
            campaigns = JsonValue.wrap("campaigns"),
            constraints = listOf("constraint"),
            messageType = "some message type",
            bypassHoldoutGroup = true
        )

        coEvery { remoteDataAccess.requiredUpdate(eq(schedule)) } returns false
        coEvery { remoteDataAccess.bestEffortRefresh(eq(schedule)) } returns true
        coEvery { audienceSelector.evaluate(any(), any()) } returns true
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo("contact id", null)
        coEvery { deviceInfoProvider.getChannelId() } returns "channel-id"
        coEvery { deviceInfoProvider.locale } returns Locale.US
        coEvery { deviceInfoProvider.isNotificationsOptedIn } returns true

        coEvery { actionPreparer.prepare(any(), any()) } answers {
            val info: PreparedScheduleInfo = secondArg()
            assertEquals(schedule.identifier, info.scheduleId)
            assertNull(info.experimentResult)

            return@answers Result.success(firstArg())
        }

        val result = preparer.prepare(schedule, triggerContext)
        if (result is SchedulePrepareResult.Prepared) {
            assertNull(result.schedule.info.experimentResult)
        } else {
            fail()
        }

        coVerify(exactly = 0) { experimentManager.evaluateExperiments(any(), any()) }

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
        val scheduleData = data ?: AutomationSchedule.ScheduleData.InAppMessageData(
            InAppMessage(
                name = "message",
                displayContent = content
            )
        )

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
        coEvery { experimentManager.evaluateExperiments(any(), any()) } returns Result.success(null)
    }
}
