/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import com.urbanairship.Airship
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.inAppAutomation
import kotlinx.coroutines.runBlocking

/**
 * Action to schedule actions.
 *
 * Accepted situations: [Action.SITUATION_MANUAL_INVOCATION], [Action.SITUATION_WEB_VIEW_INVOCATION],
 * [Action.SITUATION_AUTOMATION], and [Action.SITUATION_PUSH_RECEIVED].
 *
 * Result value: Schedule ID.
 *
 * Default Registration Names: [ScheduleAction.DEFAULT_NAMES]
 *
 * Default Registration Predicate: none
 */
public class ScheduleAction
@JvmOverloads
constructor(
    private val scheduler: suspend (AutomationSchedule) -> Unit = {
        Airship.inAppAutomation.upsertSchedules(listOf(it))
    }
) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when(arguments.situation) {
            Situation.MANUAL_INVOCATION,
            Situation.WEB_VIEW_INVOCATION,
            Situation.PUSH_RECEIVED,
            Situation.AUTOMATION -> true
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val schedule = AutomationSchedule.fromJson(arguments.value.toJsonValue())

        runBlocking { scheduler.invoke(schedule) }

        return ActionResult.newResult(ActionValue.wrap(schedule.identifier))
    }

    public companion object {
        /** Default registry name */
        @JvmStatic
        public val DEFAULT_NAMES: Set<String> = setOf("schedule_actions", "^sa")
    }
}
