/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.InAppMessage

internal class InAppMessageAnalyticsFactory(
    private val eventRecorder: InAppEventRecorderInterface,
    private val displayHistoryStore: MessageDisplayHistoryStore,
    private val displayImpressionRuleProvider: InAppDisplayImpressionRuleInterface
) {
    suspend fun makeAnalytics(
        message: InAppMessage,
        preparedScheduleInfo: PreparedScheduleInfo,
    ) : InAppMessageAnalyticsInterface {
        return InAppMessageAnalytics(
            preparedScheduleInfo = preparedScheduleInfo,
            message = message,
            displayImpressionRule = displayImpressionRuleProvider.impressionRules(message),
            eventRecorder = eventRecorder,
            historyStore = displayHistoryStore,
            displayHistory = displayHistoryStore.get(preparedScheduleInfo.scheduleId)
        )
    }
}
