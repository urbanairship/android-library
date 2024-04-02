package com.urbanairship.automation.rewrite.engine

import com.urbanairship.automation.rewrite.inappmessage.PreparedInAppMessageData
import com.urbanairship.automation.rewrite.limits.FrequencyCheckerInterface
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Objects
import kotlin.jvm.Throws

internal data class PreparedSchedule(
    val info: PreparedScheduleInfo,
    val data: PreparedScheduleData,
    val frequencyChecker: FrequencyCheckerInterface?
)

internal sealed class PreparedScheduleData {
    data class InAppMessage(val message: PreparedInAppMessageData) : PreparedScheduleData()
    data class Action(val json: JsonValue) : PreparedScheduleData()
}

internal data class PreparedScheduleInfo(
    val scheduleID: String,
    val productID: String? = null,
    val campaigns: JsonValue? = null,
    val contactID: String? = null,
    val experimentResult: ExperimentResult? = null,
    val reportingContext: JsonValue? = null
) : JsonSerializable {
    companion object {
        private const val SCHEDULE_ID = "schedule_id"
        private const val PRODUCT_ID = "product_id"
        private const val CAMPAIGNS = "campaigns"
        private const val CONTACT_ID = "contact_id"
        private const val EXPERIMENT_RESULT = "experiment_result"
        private const val REPORTING_CONTEXT = "reporting_context"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): PreparedScheduleInfo {
            val content = value.requireMap()
            return PreparedScheduleInfo(
                scheduleID = content.requireField(SCHEDULE_ID),
                productID = content.optionalField(PRODUCT_ID),
                campaigns = content.get(CAMPAIGNS),
                contactID = content.optionalField(CONTACT_ID),
                experimentResult = content.get(EXPERIMENT_RESULT)?.let { ExperimentResult.fromJson(it.requireMap()) },
                reportingContext = content.get(REPORTING_CONTEXT)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SCHEDULE_ID to scheduleID,
        PRODUCT_ID to productID,
        CAMPAIGNS to campaigns,
        CONTACT_ID to contactID,
        EXPERIMENT_RESULT to experimentResult,
        REPORTING_CONTEXT to reportingContext
    ).toJsonValue()
}
