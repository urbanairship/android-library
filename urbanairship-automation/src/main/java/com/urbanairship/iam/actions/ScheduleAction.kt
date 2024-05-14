package com.urbanairship.iam.actions

import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InAppAutomation
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
        InAppAutomation.shared().upsertSchedules(listOf(it))
    }
) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when(arguments.situation) {
            SITUATION_MANUAL_INVOCATION,
            SITUATION_WEB_VIEW_INVOCATION,
            SITUATION_PUSH_RECEIVED,
            SITUATION_AUTOMATION -> true
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
        public val DEFAULT_NAMES: List<String> = listOf("schedule_actions", "^sa")
    }
}
