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
import com.urbanairship.automation.engine.recordExecution
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.engine.ScheduleReadyResult
import com.urbanairship.automation.limits.AutomationLedgerInterface
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.json.JsonValue

internal class ActionAutomationExecutor(
    private val ledger: AutomationLedgerInterface,
    val actionRunner: ActionRunner = DefaultActionRunner
) : AutomationExecutorDelegate<JsonValue> {

    override fun isReady(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleReadyResult = ScheduleReadyResult.READY

    override suspend fun execute(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleExecuteResult {

        if (!preparedScheduleInfo.additionalAudienceCheckResult) {
            // The attempt still resolved and still spends the schedule's budget,
            // so it has to reach the ledger. Without an event the limit can
            // never be reached and the schedule re-triggers forever.
            ledger.recordExecution(preparedScheduleInfo, LedgerExecutionResult.AUDIENCE_MISS)
            return ScheduleExecuteResult.FINISHED
        }

        actionRunner.runSuspending(data.optMap().map, Action.Situation.AUTOMATION)

        ledger.recordExecution(preparedScheduleInfo, LedgerExecutionResult.SUCCEEDED)

        return ScheduleExecuteResult.FINISHED
    }

    override suspend fun interrupted(
        schedule: AutomationSchedule, preparedScheduleInfo: PreparedScheduleInfo
    ): InterruptedBehavior = InterruptedBehavior.RETRY
}
