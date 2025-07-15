/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.runBlocking

/**
 * Action to cancel automation schedules.
 *
 * Accepted situations: SITUATION_MANUAL_INVOCATION, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_AUTOMATION, and SITUATION_PUSH_RECEIVED.
 *
 * Accepted argument value - Either [CancelSchedulesAction.ALL] or a map with:
 * * [CancelSchedulesAction.GROUPS]: List of schedule groups or a single group. Optional.
 * * [CancelSchedulesAction.IDS]: List of schedule IDs or a single schedule Id. Optional.
 *
 * Result value: null.
 *
 * Default Registration Names: [CancelSchedulesAction.DEFAULT_NAMES]
 *
 * Default Registration Predicate: none
 */

public class CancelSchedulesAction
@JvmOverloads
constructor(
    private val automationGetter: () -> InAppAutomation = { InAppAutomation.shared() }
) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when(arguments.situation) {
            Situation.MANUAL_INVOCATION,
            Situation.WEB_VIEW_INVOCATION,
            Situation.AUTOMATION,
            Situation.PUSH_RECEIVED -> true
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val args = Arguments.fromJson(arguments.value.toJsonValue())
        val automation = automationGetter.invoke()

        runBlocking {
            if (args.cancelAll) {
                automation.cancelSchedulesWith(AutomationSchedule.ScheduleType.ACTIONS)
                return@runBlocking
            }

            args.groups?.let {
                for (item in it) {
                    automation.cancelSchedules(item)
                }
            }

            args.scheduleIDs?.let {
                automation.cancelSchedules(it)
            }
        }

        return ActionResult.newEmptyResult()
    }

    public companion object {
        /** Default registry name */
        @JvmStatic
        public val DEFAULT_NAMES: List<String> = listOf("cancel_scheduled_actions", "^csa")

        /** Used as the key in the action's value map to specify schedule groups to cancel. */
        @JvmStatic
        public val GROUPS: String = "groups"

        /** Used as the key in the action's value map to specify schedule IDs to cancel. */
        @JvmStatic
        public val IDS: String = "ids"

        /** Used as the action's value to cancel all schedules. */
        @JvmStatic
        public val ALL: String = "all"
    }

    private data class Arguments(
        val cancelAll: Boolean,
        val scheduleIDs: List<String>? = null,
        val groups: List<String>? = null
    ) {
        companion object {

            @Throws(IllegalArgumentException::class)
            fun fromJson(value: JsonValue): Arguments {
                fun getSingleOrList(json: JsonValue): List<String> {
                    return if (json.isString) {
                        listOf(json.getString(""))
                    } else {
                        json.optList().mapNotNull { it.string }
                    }
                }

                val cancelAll = value.string?.let { it.lowercase() == ALL } ?: false
                val cancelIDs = value.optMap().get(IDS)?.let { getSingleOrList(it) }
                val cancelGroups = value.optMap().get(GROUPS)?.let { getSingleOrList(it) }

                if (!cancelAll && cancelIDs == null && cancelGroups == null) {
                    throw IllegalArgumentException()
                }

                return Arguments(
                    cancelAll = cancelAll,
                    scheduleIDs = cancelIDs,
                    groups = cancelGroups
                )
            }
        }
    }
}
