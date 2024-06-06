/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.RestrictTo
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class PreparedSchedule(
    internal val info: PreparedScheduleInfo,
    internal val data: PreparedScheduleData,
    internal val frequencyChecker: FrequencyChecker?
)

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal sealed class PreparedScheduleData {
    data class InAppMessage(val message: PreparedInAppMessageData) : PreparedScheduleData()
    data class Action(val json: JsonValue) : PreparedScheduleData()
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class PreparedScheduleInfo(
    internal val scheduleId: String,
    internal val productId: String? = null,
    internal val campaigns: JsonValue? = null,
    internal val contactId: String? = null,
    internal val experimentResult: ExperimentResult? = null,
    internal val reportingContext: JsonValue? = null,
    internal val triggerSessionId: String,
    internal val additionalAudienceCheckResult: Boolean = true
) : JsonSerializable {

    internal companion object {
        private const val SCHEDULE_ID = "schedule_id"
        private const val PRODUCT_ID = "product_id"
        private const val CAMPAIGNS = "campaigns"
        private const val CONTACT_ID = "contact_id"
        private const val EXPERIMENT_RESULT = "experiment_result"
        private const val REPORTING_CONTEXT = "reporting_context"
        private const val TRIGGER_SESSION_ID = "trigger_session_id"
        private const val ADDITIONAL_AUDIENCE_CHECK_RESULT = "additional_audience_check_result"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): PreparedScheduleInfo {
            val content = value.requireMap()
            return PreparedScheduleInfo(
                scheduleId = content.requireField(SCHEDULE_ID),
                productId = content.optionalField(PRODUCT_ID),
                campaigns = content.get(CAMPAIGNS),
                contactId = content.optionalField(CONTACT_ID),
                experimentResult = content.get(EXPERIMENT_RESULT)?.let { ExperimentResult.fromJson(it.requireMap()) },
                reportingContext = content.get(REPORTING_CONTEXT),
                triggerSessionId = content.requireField(TRIGGER_SESSION_ID),
                additionalAudienceCheckResult = content.requireField(ADDITIONAL_AUDIENCE_CHECK_RESULT)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SCHEDULE_ID to scheduleId,
        PRODUCT_ID to productId,
        CAMPAIGNS to campaigns,
        CONTACT_ID to contactId,
        EXPERIMENT_RESULT to experimentResult,
        REPORTING_CONTEXT to reportingContext,
        TRIGGER_SESSION_ID to triggerSessionId,
        ADDITIONAL_AUDIENCE_CHECK_RESULT to additionalAudienceCheckResult
    ).toJsonValue()
}
