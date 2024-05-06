package com.urbanairship.automation.rewrite.actionautomation

import com.urbanairship.actions.Action
import com.urbanairship.automation.rewrite.AutomationExecutorDelegate
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.InterruptedBehavior
import com.urbanairship.automation.rewrite.ScheduleExecuteResult
import com.urbanairship.automation.rewrite.ScheduleReadyResult
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.InAppActionUtils
import com.urbanairship.json.JsonValue

internal class ActionAutomationExecutor : AutomationExecutorDelegate<JsonValue> {

    override fun isReady(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleReadyResult = ScheduleReadyResult.READY

    override suspend fun execute(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleExecuteResult {
        InAppActionUtils.runActions(data.optMap().map, situation = Action.SITUATION_AUTOMATION)
        return ScheduleExecuteResult.FINISHED
    }

    override suspend fun interrupted(
        schedule: AutomationSchedule, preparedScheduleInfo: PreparedScheduleInfo
    ): InterruptedBehavior = InterruptedBehavior.RETRY
}
