package com.urbanairship.automation.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.iam.actions.CancelSchedulesAction
import com.urbanairship.json.jsonMapOf
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CancelSchedulesActionTest {
    private val automation: InAppAutomation = mockk(relaxed = true)
    private val action = CancelSchedulesAction(automationGetter = { automation })

    @Test
    public fun testAcceptsSituations() {
        val valid = listOf(
            Action.SITUATION_MANUAL_INVOCATION,
            Action.SITUATION_WEB_VIEW_INVOCATION,
            Action.SITUATION_PUSH_RECEIVED,
            Action.SITUATION_AUTOMATION
        )

        val rejected = listOf(
            Action.SITUATION_PUSH_OPENED,
            Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        )

        valid.forEach { situation ->
            assertTrue(action.acceptsArguments(ActionArguments(situation, null, null)))
        }

        rejected.forEach { situation ->
            assertFalse(action.acceptsArguments(ActionArguments(situation, null, null)))
        }
    }

    @Test
    public fun testArguments(): TestResult = runTest {
        //should accept all
        var args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap("all"), null)
        assertEquals(ActionResult.STATUS_COMPLETED, action.perform(args).status)
        coVerify { automation.cancelSchedulesWith(AutomationSchedule.ScheduleType.ACTIONS) }

        //should fail other strings
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap("invalid"), null)
        assertThrows(IllegalArgumentException::class.java) { action.perform(args) }

        //should accept dictionaries with groups
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(
            jsonMapOf("groups" to "test")
        ), null)
        assertEquals(ActionResult.STATUS_COMPLETED, action.perform(args).status)
        coVerify { automation.cancelSchedules("test") }

        //should accept dictionaries with groups array
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(
            jsonMapOf("groups" to listOf("group-list-item"))
        ), null)
        assertEquals(ActionResult.STATUS_COMPLETED, action.perform(args).status)
        coVerify { automation.cancelSchedules("group-list-item") }

        //should accept dictionaries with ids
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(
            jsonMapOf("ids" to "test")
        ), null)
        assertEquals(ActionResult.STATUS_COMPLETED, action.perform(args).status)
        coVerify { automation.cancelSchedules(listOf("test")) }

        //should accept dictionaries with ids array
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(
            jsonMapOf("ids" to listOf("id-from-list"))
        ), null)
        assertEquals(ActionResult.STATUS_COMPLETED, action.perform(args).status)
        coVerify { automation.cancelSchedules(listOf("id-from-list")) }

        //should accept dictionaries with ids and groups
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(
            jsonMapOf(
                "ids" to listOf("id-from-list1"),
                "groups" to "group1")
        ), null)
        assertEquals(ActionResult.STATUS_COMPLETED, action.perform(args).status)
        coVerify { automation.cancelSchedules(listOf("id-from-list1")) }
        coVerify { automation.cancelSchedules("group1") }

        //should fail if neither groups nor ids key found
        args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap("neither"), null)
        assertThrows(IllegalArgumentException::class.java) { action.perform(args) }
    }
}
