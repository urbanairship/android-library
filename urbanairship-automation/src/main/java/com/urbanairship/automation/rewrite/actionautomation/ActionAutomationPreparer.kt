package com.urbanairship.automation.rewrite.actionautomation

import com.urbanairship.automation.rewrite.engine.AutomationPreparerDelegate
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.json.JsonValue

internal class ActionAutomationPreparer : AutomationPreparerDelegate<JsonValue, JsonValue> {

    override suspend fun prepare(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): Result<JsonValue> = Result.success(data)

    override suspend fun cancelled(scheduleID: String) {
        // no-op
    }
}
