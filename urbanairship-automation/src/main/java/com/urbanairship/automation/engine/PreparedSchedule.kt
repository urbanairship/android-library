/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.RestrictTo
import com.urbanairship.automation.limits.AutomationLedgerInterface
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.UUID

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
    internal val additionalAudienceCheckResult: Boolean = true,
    internal val priority: Int = 0,
    internal val sendMetadata: String? = null,
    /**
     * Shared ledger group ID the schedule had when prepared. Stamped here so
     * execution-outcome ledger events record under it without re-reading the
     * (possibly changed) schedule config.
     */
    internal val ledgerSharedId: String? = null,
    /**
     * ID of the execution-causing trigger, carried through for ledger event
     * attribution.
     */
    internal val triggerId: String? = null
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
        private const val PRIORITY = "PRIORITY"
        private const val SEND_METADATA = "send_metadata"
        private const val LEDGER_SHARED_ID = "ledger_shared_id"
        private const val TRIGGER_ID = "trigger_id"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): PreparedScheduleInfo {
            val content = value.requireMap()
            return PreparedScheduleInfo(
                scheduleId = content.requireField(SCHEDULE_ID),
                productId = content.optionalField(PRODUCT_ID),
                campaigns = content[CAMPAIGNS],
                contactId = content.optionalField(CONTACT_ID),
                experimentResult = content[EXPERIMENT_RESULT]?.let { ExperimentResult.fromJson(it.requireMap()) },
                reportingContext = content[REPORTING_CONTEXT],
                // Default to a UUID for backwards compatibility
                triggerSessionId = content.optionalField(TRIGGER_SESSION_ID) ?: UUID.randomUUID().toString(),
                additionalAudienceCheckResult = content.optionalField(ADDITIONAL_AUDIENCE_CHECK_RESULT) ?: true,
                priority = content.optionalField(PRIORITY) ?: 0,
                sendMetadata = content.optionalField(SEND_METADATA),
                ledgerSharedId = content.optionalField(LEDGER_SHARED_ID),
                triggerId = content.optionalField(TRIGGER_ID),
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
        ADDITIONAL_AUDIENCE_CHECK_RESULT to additionalAudienceCheckResult,
        PRIORITY to priority,
        SEND_METADATA to sendMetadata,
        LEDGER_SHARED_ID to ledgerSharedId,
        TRIGGER_ID to triggerId
    ).toJsonValue()
}

/**
 * Records the outcome of a prepared attempt.
 *
 * The single recorder every executor funnels through, so the mapping from
 * [PreparedScheduleInfo] onto the ledger's scope keys lives in one place and a
 * new budget-consuming outcome cannot quietly skip it.
 */
internal suspend fun AutomationLedgerInterface.recordExecution(
    info: PreparedScheduleInfo,
    result: LedgerExecutionResult,
    cancel: Boolean = false
) {
    recordExecution(
        scheduleId = info.scheduleId,
        sharedId = info.ledgerSharedId,
        triggerId = info.triggerId,
        result = result,
        cancel = cancel
    )
}
