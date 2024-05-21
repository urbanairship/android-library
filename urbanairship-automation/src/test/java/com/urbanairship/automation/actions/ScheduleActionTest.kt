package com.urbanairship.automation.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionValue
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.iam.actions.ScheduleAction
import com.urbanairship.json.JsonException
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ScheduleActionTest {
    private var scheduled: AutomationSchedule? = null
    private val action = ScheduleAction(scheduler = { scheduled = it })

    @Test
    public fun testAcceptsArguments() {
        val valid = listOf(
            Action.SITUATION_MANUAL_INVOCATION,
            Action.SITUATION_WEB_VIEW_INVOCATION,
            Action.SITUATION_PUSH_RECEIVED,
            Action.SITUATION_AUTOMATION
        )

        val rejected = listOf(
            Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.SITUATION_PUSH_OPENED
        )

        for (situation in valid) {
            assertTrue(action.acceptsArguments(ActionArguments(situation, null, null)))
        }

        for (situation in rejected) {
            assertFalse(action.acceptsArguments(ActionArguments(situation, null, null)))
        }
    }

    @Test
    public fun testSchedule(): TestResult = runTest {
        val scheduleJson = jsonMapOf(
            "id" to "test-id",
            "type" to "actions",
            "group" to "test-group",
            "limit" to 1,
            "actions" to jsonMapOf(
                "action-name" to "action-value"
            ),
            "start" to DateUtils.createIso8601TimeStamp(0),
            "end" to DateUtils.createIso8601TimeStamp(1000),
            "triggers" to listOf(
                jsonMapOf(
                    "type" to "foreground",
                    "goal" to 2.0
                )
            ),
            "created" to DateUtils.createIso8601TimeStamp(0),
        )

        assertNull(scheduled)

        val scheduleID = action.perform(ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(scheduleJson), null)).value.string
        assertEquals("test-id", scheduleID)

        assertEquals("test-id", scheduled?.identifier)
        assertEquals("test-group", scheduled?.group)
        assertEquals(1U, scheduled?.limit)
        assertEquals(1000UL, scheduled?.endDate)
        assertEquals(0UL, scheduled?.startDate)
        assertEquals(1, scheduled?.triggers?.size)
        assertEquals(EventAutomationTriggerType.FOREGROUND.value, scheduled?.triggers?.first()?.type)
        assertEquals(2.0, scheduled?.triggers?.first()?.goal)

        val actionJson = scheduled?.data as? AutomationSchedule.ScheduleData.Actions
        assertEquals(jsonMapOf(
            "action-name" to "action-value"
        ), actionJson?.actions)
    }

    @Test
    public fun testScheduleThrowsOnInvalidSource(): TestResult = runTest {
        assertThrows(JsonException::class.java) { action.perform(ActionArguments(
            Action.SITUATION_AUTOMATION,
            ActionValue.wrap(jsonMapOf()),
            null))}
    }
}
