package com.urbanairship.automation.action

import com.urbanairship.actions.Action
import com.urbanairship.automation.AutomationExecutorDelegate
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.InterruptedBehavior
import com.urbanairship.automation.ScheduleExecuteResult
import com.urbanairship.automation.ScheduleReadyResult
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.InAppActionUtils
import com.urbanairship.json.JsonValue

internal class ActionAutomationExecutor : AutomationExecutorDelegate<JsonValue> {

    override fun isReady(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleReadyResult = ScheduleReadyResult.READY

    override suspend fun execute(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleExecuteResult {
        InAppActionUtils.runActions(data.optMap(), situation = Action.SITUATION_AUTOMATION)
        return ScheduleExecuteResult.FINISHED
    }

    override suspend fun interrupted(
        schedule: AutomationSchedule, preparedScheduleInfo: PreparedScheduleInfo
    ): InterruptedBehavior = InterruptedBehavior.RETRY
}
