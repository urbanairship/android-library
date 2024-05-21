package com.urbanairship.iam.analytics

import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.InAppMessage
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonValue
import com.urbanairship.meteredusage.AirshipMeteredUsage

internal class InAppMessageAnalyticsFactory(
    private val eventRecorder: InAppEventRecorderInterface,
    private val impressionRecorder: AirshipMeteredUsage
) {
    fun makeAnalytics(
        scheduleID: String,
        productID: String?,
        contactID: String?,
        message: InAppMessage,
        campaigns: JsonValue?,
        reportingContext: JsonValue?,
        experimentResult: ExperimentResult?
    ) : InAppMessageAnalyticsInterface {
        return InAppMessageAnalytics(
            scheduleId = scheduleID,
            productId = productID,
            contactId = contactID,
            message = message,
            campaigns = campaigns,
            reportingMetadata = reportingContext,
            experimentResult = experimentResult,
            eventRecorder = eventRecorder,
            impressionRecorder = impressionRecorder,
        )
    }

    fun makeAnalytics(
        message: InAppMessage,
        preparedScheduleInfo: PreparedScheduleInfo
    ) : InAppMessageAnalyticsInterface {
        return InAppMessageAnalytics(
            scheduleId = preparedScheduleInfo.scheduleId,
            productId = preparedScheduleInfo.productId,
            contactId = preparedScheduleInfo.contactId,
            message = message,
            campaigns = preparedScheduleInfo.campaigns,
            reportingMetadata = preparedScheduleInfo.reportingContext,
            experimentResult = preparedScheduleInfo.experimentResult,
            eventRecorder = eventRecorder,
            impressionRecorder = impressionRecorder,
        )
    }
}
