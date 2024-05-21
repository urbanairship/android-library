package com.urbanairship.automation.actions

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionArguments.PUSH_MESSAGE_METADATA
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.HTML
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.actions.LandingPageAction
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushMessage
import com.urbanairship.push.PushMessage.EXTRA_SEND_ID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LandingPageActionTest {

    @Test
    public fun testAcceptsArguments() {
        val action = LandingPageAction()

        val validSituations = listOf(
            Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.SITUATION_PUSH_OPENED,
            Action.SITUATION_MANUAL_INVOCATION,
            Action.SITUATION_WEB_VIEW_INVOCATION,
            Action.SITUATION_AUTOMATION,
        )

        val rejectedSituations = listOf(
            Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.SITUATION_PUSH_RECEIVED
        )

        validSituations.forEach { situation ->
            assertTrue(action.acceptsArguments(ActionArguments(situation, null, null)))
        }

        rejectedSituations.forEach { situation ->
            assertFalse(action.acceptsArguments(ActionArguments(situation, null, null)))
        }
    }

    @Test
    public fun testSimpleURLArg(): TestResult = runTest {
        val urlCheckedJob = Job()
        val scheduleCalledJob = Job()

        val expectedMessage = InAppMessage(
            name = "Landing Page https://some-url",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(
                    url = "https://some-url",
                    requiresConnectivity = false,
                    borderRadius = 10f,
                    allowFullscreenDisplay = false
                )
            ),
            isReportingEnabled = false,
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val action = LandingPageAction(
            borderRadius = 10f,
            scheduleExtender = null,
            allowListChecker = { url: String ->
                assertEquals("https://some-url", url)
                urlCheckedJob.complete()
                true
            },
            scheduler = { schedule: AutomationSchedule ->
                assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(expectedMessage))
                assertEquals(schedule.triggers.size, 1)
                assertEquals(schedule.triggers[0].type, EventAutomationTriggerType.ACTIVE_SESSION.value)
                assertEquals(schedule.triggers[0].goal, 1.0)
                assertTrue(schedule.bypassHoldoutGroups == true)
                assertEquals(schedule.productId, "landing_page")
                assertEquals(schedule.queue, "landing_page")
                assertEquals(schedule.priority, Int.MIN_VALUE)
                scheduleCalledJob.complete()
            }
        )

        val args = ActionArguments(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap("https://some-url"), null)

        val result = action.perform(args)
        assertEquals(result.status, ActionResult.STATUS_COMPLETED)

        urlCheckedJob.join()
        scheduleCalledJob.join()
    }

    @Test
    public fun testDictionaryArgs(): TestResult = runTest {
        val urlCheckedJob = Job()
        val scheduleCalledJob = Job()

        val expectedMessage = InAppMessage(
            name = "Landing Page https://some-url",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(
                    url = "https://some-url",
                    requiresConnectivity = false,
                    borderRadius = 10f,
                    allowFullscreenDisplay = false,
                    width = 10,
                    height = 20,
                    aspectLock = true
                )
            ),
            isReportingEnabled = false,
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val action = LandingPageAction(
            borderRadius = 10f,
            scheduleExtender = null,
            allowListChecker = { url: String ->
                assertEquals("https://some-url", url)
                urlCheckedJob.complete()
                true
            },
            scheduler = { schedule: AutomationSchedule ->
                assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(expectedMessage))
                assertEquals(schedule.triggers.size, 1)
                assertEquals(schedule.triggers[0].type, EventAutomationTriggerType.ACTIVE_SESSION.value)
                assertEquals(schedule.triggers[0].goal, 1.0)
                assertTrue(schedule.bypassHoldoutGroups == true)
                assertEquals(schedule.productId, "landing_page")
                assertEquals(schedule.queue, "landing_page")
                assertEquals(schedule.priority, Int.MIN_VALUE)
                scheduleCalledJob.complete()
            }
        )

        val argsJson = jsonMapOf(
            "url" to "https://some-url",
            "width" to 10,
            "height" to  20,
            "aspect_lock" to true
        )

        val args = ActionArguments(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(argsJson), null)

        val result = action.perform(args)
        assertEquals(result.status, ActionResult.STATUS_COMPLETED)

        urlCheckedJob.join()
        scheduleCalledJob.join()
    }

    @Test
    public fun testAppendSchema(): TestResult = runTest {
        val urlCheckedJob = Job()
        val scheduleCalledJob = Job()

        val expectedMessage = InAppMessage(
            name = "Landing Page https://some-url",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(
                    url = "https://some-url",
                    requiresConnectivity = false,
                    borderRadius = 10f,
                    allowFullscreenDisplay = false
                )
            ),
            isReportingEnabled = false,
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val action = LandingPageAction(
            borderRadius = 10f,
            scheduleExtender = null,
            allowListChecker = { url: String ->
                assertEquals("https://some-url", url)
                urlCheckedJob.complete()
                true
            },
            scheduler = { schedule: AutomationSchedule ->
                assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(expectedMessage))
                assertEquals(schedule.triggers.size, 1)
                assertEquals(schedule.triggers[0].type, EventAutomationTriggerType.ACTIVE_SESSION.value)
                assertEquals(schedule.triggers[0].goal, 1.0)
                assertTrue(schedule.bypassHoldoutGroups == true)
                assertEquals(schedule.productId, "landing_page")
                assertEquals(schedule.queue, "landing_page")
                assertEquals(schedule.priority, Int.MIN_VALUE)
                scheduleCalledJob.complete()
            }
        )

        val args = ActionArguments(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap("some-url"), null)

        val result = action.perform(args)
        assertEquals(result.status, ActionResult.STATUS_COMPLETED)

        urlCheckedJob.join()
        scheduleCalledJob.join()
    }

    @Test
    public fun testExtendSchedule(): TestResult = runTest {
        val urlCheckedJob = Job()
        val scheduleCalledJob = Job()

        val expectedMessage = InAppMessage(
            name = "Landing Page https://some-url",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(
                    url = "https://some-url",
                    requiresConnectivity = false,
                    borderRadius = 10f,
                    allowFullscreenDisplay = false
                )
            ),
            isReportingEnabled = false,
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val action = LandingPageAction(
            borderRadius = 10f,
            scheduleExtender = { args: ActionArguments, schedule: AutomationSchedule ->
                schedule.copyWith(
                    group = "some-group",

                )
            },
            allowListChecker = { url: String ->
                assertEquals("https://some-url", url)
                urlCheckedJob.complete()
                true
            },
            scheduler = { schedule: AutomationSchedule ->
                assertEquals(schedule.data, AutomationSchedule.ScheduleData.InAppMessageData(expectedMessage))
                assertEquals(schedule.triggers.size, 1)
                assertEquals(schedule.triggers[0].type, EventAutomationTriggerType.ACTIVE_SESSION.value)
                assertEquals(schedule.triggers[0].goal, 1.0)
                assertTrue(schedule.bypassHoldoutGroups == true)
                assertEquals(schedule.productId, "landing_page")
                assertEquals(schedule.queue, "landing_page")
                assertEquals(schedule.priority, Int.MIN_VALUE)
                assertEquals(schedule.group, "some-group")
                scheduleCalledJob.complete()
            }
        )

        val args = ActionArguments(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap("https://some-url"), null)

        val result = action.perform(args)
        assertEquals(result.status, ActionResult.STATUS_COMPLETED)

        urlCheckedJob.join()
        scheduleCalledJob.join()
    }

    @Test
    public fun testRejectsURL(): TestResult = runTest {
        val checkJob = Job()
        val action = LandingPageAction(
            borderRadius = 2f,
            allowListChecker = { url: String ->
                assertEquals("https://some-url", url)
                checkJob.complete()
                false
            },
            scheduler = { _ ->
                fail()
            }
        )

        val args = ActionArguments(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap("https://some-url"), null)

        assertThrows(IllegalArgumentException::class.java) { action.perform(args) }
        checkJob.join()
    }

    @Test
    public fun testReportingEnabled(): TestResult = runTest {
        val scheduledJob = Job()

        val metadata = Bundle().also {
            it.putParcelable(PUSH_MESSAGE_METADATA, PushMessage(mapOf(
                EXTRA_SEND_ID to "some-send-ID"
            )))
        }

        val action = LandingPageAction(
            borderRadius = 2f,
            allowListChecker = { _ -> true },
            scheduler = { schedule: AutomationSchedule ->
                assertEquals(schedule.identifier, "some-send-ID")
                scheduledJob.complete()
            }
        )

        val args = ActionArguments(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap("https://some-url"), metadata)
        action.perform(args)
        scheduledJob.join()
    }
}
