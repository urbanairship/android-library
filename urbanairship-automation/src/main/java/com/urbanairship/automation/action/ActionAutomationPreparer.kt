/* Copyright Airship and Contributors */

package com.urbanairship.automation.action

import com.urbanairship.automation.engine.AutomationPreparerDelegate
import com.urbanairship.automation.engine.DelegatePreparerResult
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.json.JsonValue

internal class ActionAutomationPreparer : AutomationPreparerDelegate<JsonValue, JsonValue> {

    override suspend fun prepare(
        data: JsonValue, preparedScheduleInfo: PreparedScheduleInfo
    ): Result<DelegatePreparerResult<JsonValue>> = Result.success(DelegatePreparerResult.Prepared(data))

    override suspend fun cancelled(scheduleID: String) {
        // no-op
    }
}
