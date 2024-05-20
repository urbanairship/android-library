package com.urbanairship.iam.legacy

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestClock
import com.urbanairship.automation.AutomationEngineInterface
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushManager
import com.urbanairship.push.notifications.NotificationActionButton
import com.urbanairship.push.notifications.NotificationActionButtonGroup
import com.urbanairship.util.DateUtils
import java.util.concurrent.TimeUnit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LegacyInAppMessagingTest {

    private val pushManager: PushManager = mockk(relaxed = true)
    private val updates = MutableSharedFlow<LegacyInAppMessageUpdate>()
    private val analytics: LegacyAnalytics = mockk(relaxed = true)
    private val engine: AutomationEngineInterface = mockk(relaxed = true)

    private val dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val clock = TestClock().apply { currentTimeMillis = 0 }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messaging = LegacyInAppMessaging(
        context = ApplicationProvider.getApplicationContext(),
        pushManager = pushManager,
        updates = updates,
        preferenceDataStore = dataStore,
        automationEngine = engine,
        legacyAnalytics = analytics,
        clock =  clock,
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    public fun testSchedule(): TestResult = runTest {
        every { pushManager.getNotificationActionGroup("ua_yes_no_background") } returns NotificationActionButtonGroup.newBuilder()
            .addNotificationActionButton(NotificationActionButton.newBuilder("yes").setLabel("yes!").build())
            .addNotificationActionButton(NotificationActionButton.newBuilder("no").setLabel("no!").build())
            .build()

        val message = LegacyInAppMessage(
            id = "test-send-id",
            placement = Banner.Placement.TOP,
            alert = "test iam",
            displayDurationMs = TimeUnit.SECONDS.toMillis(100),
            expiryMs = DateUtils.parseIso8601("2024-08-13T23:33:04"),
            clickActionValues = jsonMapOf("onclick" to "action"),
            buttonGroupId = "ua_yes_no_background",
            buttonActionValues = mapOf("yes" to jsonMapOf("action_one" to 123)),
            primaryColor = Color.parseColor("#ABCDEF"),
            secondaryColor = Color.parseColor("#FEDCBA"),
            messageType = "transactional",
            campaigns = jsonMapOf("test-campaign" to "json").toJsonValue(),
            extras = jsonMapOf("one" to 2)
        )

        updates.emit(LegacyInAppMessageUpdate.NewMessage(message))

        val primaryColor = InAppMessageColor(message.primaryColor!!)
        val secondaryColor = InAppMessageColor(message.secondaryColor!!)

        val expected = InAppMessage(
            name = message.id,
            displayContent =  InAppMessageDisplayContent.BannerContent(
                banner = Banner(
                    backgroundColor = primaryColor,
                    dismissButtonColor = secondaryColor,
                    borderRadius = LegacyInAppMessaging.DEFAULT_BORDER_RADIUS_DP,
                    actions = message.clickActionValues,
                    durationMs = message.displayDurationMs!!,
                    placement = message.placement,
                    template = Banner.Template.MEDIA_LEFT,
                    body = InAppMessageTextInfo(message.alert!!, color = secondaryColor),
                    buttonLayoutType = InAppMessageButtonLayoutType.SEPARATE,
                    buttons = listOf(
                        InAppMessageButtonInfo(
                            identifier = "yes",
                            label = InAppMessageTextInfo(
                                text = "yes!",
                                color = primaryColor,
                                alignment = InAppMessageTextInfo.Alignment.CENTER,
                                drawableName = null
                            ),
                            actions = jsonMapOf("action_one" to 123),
                            backgroundColor = secondaryColor,
                            borderRadius = LegacyInAppMessaging.DEFAULT_BORDER_RADIUS_DP
                        ),
                        InAppMessageButtonInfo(
                            identifier = "no",
                            label = InAppMessageTextInfo(
                                text = "no!",
                                color = primaryColor,
                                alignment = InAppMessageTextInfo.Alignment.CENTER,
                                drawableName = null
                            ),
                            backgroundColor = secondaryColor,
                            borderRadius = LegacyInAppMessaging.DEFAULT_BORDER_RADIUS_DP
                        ),
                    )
                )
            ),
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH,
            extras = message.extras,
        )

        coVerify {
            engine.upsertSchedules(
                match {
                    val schedule = it[0]
                    if (schedule.identifier != message.id) { return@match false }
                    if (schedule.endDate != message.expiryMs!!.toULong()) { return@match false }
                    if (schedule.triggers[0].type != EventAutomationTriggerType.ACTIVE_SESSION.value) { return@match false }
                    if (schedule.data != AutomationSchedule.ScheduleData.InAppMessageData(expected)) { return@match false }
                    true
                }
            )
        }
    }

    @Test
    public fun testScheduleMinMessage(): TestResult = runTest {
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))

        val expected = InAppMessage(
            name = "some-id",
            displayContent =  InAppMessageDisplayContent.BannerContent(
                banner = Banner(
                    backgroundColor = InAppMessageColor(LegacyInAppMessaging.DEFAULT_PRIMARY_COLOR),
                    dismissButtonColor = InAppMessageColor(LegacyInAppMessaging.DEFAULT_SECONDARY_COLOR),
                    borderRadius = LegacyInAppMessaging.DEFAULT_BORDER_RADIUS_DP,
                    durationMs = Banner.DEFAULT_DURATION_MS,
                    placement = Banner.Placement.TOP,
                    template = Banner.Template.MEDIA_LEFT,
                    body = InAppMessageTextInfo("", color = InAppMessageColor(LegacyInAppMessaging.DEFAULT_SECONDARY_COLOR)),
                    buttonLayoutType = InAppMessageButtonLayoutType.SEPARATE
                )
            ),
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH
        )

        coVerify {
            engine.upsertSchedules(
                match {
                    val schedule = it[0]
                    if (schedule.identifier != "some-id") { return@match false }
                    if (schedule.endDate != LegacyInAppMessaging.DEFAULT_EXPIRY_MS.toULong()) { return@match false }
                    if (schedule.triggers[0].type != EventAutomationTriggerType.ACTIVE_SESSION.value) { return@match false }
                    if (schedule.data != AutomationSchedule.ScheduleData.InAppMessageData(expected)) { return@match false }
                    true
                }
            )
        }
    }

    @Test
    public fun testScheduleAsapDisabled(): TestResult = runTest {
        messaging.displayAsapEnabled = false

        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))

        coVerify {
            engine.upsertSchedules(
                match {
                    it[0].triggers[0].type == EventAutomationTriggerType.FOREGROUND.value
                }
            )
        }
    }

    @Test
    public fun testExtendMessage(): TestResult = runTest {
        val original = InAppMessage(
            name = "some-id",
            displayContent =  InAppMessageDisplayContent.BannerContent(
                banner = Banner(
                    backgroundColor = InAppMessageColor(LegacyInAppMessaging.DEFAULT_PRIMARY_COLOR),
                    dismissButtonColor = InAppMessageColor(LegacyInAppMessaging.DEFAULT_SECONDARY_COLOR),
                    borderRadius = LegacyInAppMessaging.DEFAULT_BORDER_RADIUS_DP,
                    durationMs = Banner.DEFAULT_DURATION_MS,
                    placement = Banner.Placement.TOP,
                    template = Banner.Template.MEDIA_LEFT,
                    body = InAppMessageTextInfo("", color = InAppMessageColor(LegacyInAppMessaging.DEFAULT_SECONDARY_COLOR)),
                    buttonLayoutType = InAppMessageButtonLayoutType.SEPARATE
                )
            ),
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH
        )

        val extended = InAppMessage(
            name = "some-name",
            displayContent =  InAppMessageDisplayContent.CustomContent(
                Custom(jsonMapOf("cool" to true).toJsonValue())
            ),
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH
        )

        messaging.messageExtender = {
            assertEquals(original, it)
            extended
        }

        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))

        coVerify {
            engine.upsertSchedules(
                match {
                    val schedule = it[0]
                    if (schedule.identifier != "some-id") { return@match false }
                    if (schedule.endDate != LegacyInAppMessaging.DEFAULT_EXPIRY_MS.toULong()) { return@match false }
                    if (schedule.triggers[0].type != EventAutomationTriggerType.ACTIVE_SESSION.value) { return@match false }
                    if (schedule.data != AutomationSchedule.ScheduleData.InAppMessageData(extended)) { return@match false }
                    true
                }
            )
        }
    }

    @Test
    public fun testExtendSchedule(): TestResult = runTest {
        val extended = AutomationSchedule(
            identifier = "extended",
            data = AutomationSchedule.ScheduleData.Actions(jsonMapOf("cool" to true).toJsonValue()),
            triggers = emptyList()
        )

        messaging.scheduleExtender = {
            extended
        }

        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))
        coVerify { engine.upsertSchedules(listOf(extended)) }
    }

    @Test
    public fun testCustomMessageConverters(): TestResult = runTest {
        val original = LegacyInAppMessage("some-id", Banner.Placement.TOP)

        val schedule = AutomationSchedule(
            identifier = "extended",
            data = AutomationSchedule.ScheduleData.Actions(jsonMapOf("cool" to true).toJsonValue()),
            triggers = emptyList()
        )

        messaging.customMessageConverter = {
            assertEquals(original, it)
            schedule
        }

        updates.emit(LegacyInAppMessageUpdate.NewMessage(original))
        coVerify { engine.upsertSchedules(listOf(schedule)) }
    }

    @Test
    public fun testReplace(): TestResult = runTest {
        coEvery { engine.getSchedule("some-id") } returns mockk()
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-other-id", Banner.Placement.TOP)))
        coVerify { analytics.recordReplacedEvent("some-id", "some-other-id") }
    }

    @Test
    public fun testReplaceNoLongerScheduled(): TestResult = runTest {
        coEvery { engine.getSchedule("some-id") } returns null
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-other-id", Banner.Placement.TOP)))
        coVerify(exactly = 0) { analytics.recordReplacedEvent(any(), any()) }
    }

    @Test
    public fun testDirectOpen(): TestResult = runTest {
        coEvery { engine.getSchedule("some-id") } returns mockk()
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))
        updates.emit(LegacyInAppMessageUpdate.DirectOpen("some-id"))
        coVerify { analytics.recordDirectOpenEvent("some-id") }
    }

    @Test
    public fun testDirectOpenNoLongerScheduled(): TestResult = runTest {
        coEvery { engine.getSchedule("some-id") } returns null
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))
        updates.emit(LegacyInAppMessageUpdate.DirectOpen("some-id"))
        coVerify(exactly = 0) { analytics.recordDirectOpenEvent(any()) }
    }

    @Test
    public fun testDirectOpenDifferentInApp(): TestResult = runTest {
        coEvery { engine.getSchedule("some-other-id") } returns mockk()
        updates.emit(LegacyInAppMessageUpdate.NewMessage(LegacyInAppMessage("some-id", Banner.Placement.TOP)))
        updates.emit(LegacyInAppMessageUpdate.DirectOpen("some-other-id"))
        coVerify(exactly = 0) { analytics.recordDirectOpenEvent(any()) }
    }

}
