/* Copyright Airship and Contributors */

package com.urbanairship.automation.action

import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionRunner
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.runSuspending
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.engine.AutomationExecutorDelegate
import com.urbanairship.automation.engine.InterruptedBehavior
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.engine.ScheduleReadyResult
import com.urbanairship.json.JsonValue

internal class ActionAutomationExecutor(val actionRunner: ActionRunner = DefaultActionRunner) : AutomationExecutorDelegate<JsonValue> {

    override fun isReady(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleReadyResult = ScheduleReadyResult.READY

    override suspend fun execute(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleExecuteResult {

        if (!preparedScheduleInfo.additionalAudienceCheckResult) {
            return ScheduleExecuteResult.FINISHED
        }

        actionRunner.runSuspending(data.optMap().map, Action.Situation.AUTOMATION)
        return ScheduleExecuteResult.FINISHED
    }

    override suspend fun interrupted(
        schedule: AutomationSchedule, preparedScheduleInfo: PreparedScheduleInfo
    ): InterruptedBehavior = InterruptedBehavior.RETRY
}
