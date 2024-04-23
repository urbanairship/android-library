package com.urbanairship.automation.rewrite.engine

import androidx.annotation.RestrictTo
import com.urbanairship.automation.rewrite.inappmessage.PreparedInAppMessageData
import com.urbanairship.automation.rewrite.limits.FrequencyCheckerInterface
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class PreparedSchedule(
    internal val info: PreparedScheduleInfo,
    internal val data: PreparedScheduleData,
    internal val frequencyChecker: FrequencyCheckerInterface?
)

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class PreparedScheduleData {
    public data class InAppMessage(val message: PreparedInAppMessageData) : PreparedScheduleData()
    public data class Action(val json: JsonValue) : PreparedScheduleData()
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class PreparedScheduleInfo(
    internal val scheduleID: String,
    internal val productID: String? = null,
    internal val campaigns: JsonValue? = null,
    internal val contactID: String? = null,
    internal val experimentResult: ExperimentResult? = null,
    internal val reportingContext: JsonValue? = null
) : JsonSerializable {
    internal companion object {
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
