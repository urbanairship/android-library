package com.urbanairship.automation.rewrite.inappmessage.analytics

import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
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
            scheduleID = scheduleID,
            productID = productID,
            contactID = contactID,
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
            scheduleID = preparedScheduleInfo.scheduleID,
            productID = preparedScheduleInfo.productID,
            contactID = preparedScheduleInfo.contactID,
            message = message,
            campaigns = preparedScheduleInfo.campaigns,
            reportingMetadata = preparedScheduleInfo.reportingContext,
            experimentResult = preparedScheduleInfo.experimentResult,
            eventRecorder = eventRecorder,
            impressionRecorder = impressionRecorder,
        )
    }
}
