/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred

import com.urbanairship.automation.AutomationAiSuppression
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.iam.InAppMessage
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField

internal data class DeferredScheduleResult(
    val isAudienceMatch: Boolean,
    val message: InAppMessage? = null,
    val actions: JsonValue? = null,

    /**
     * Miss behavior resolved server side. When present it replaces the schedule's
     * audience miss behavior for this resolution, letting one schedule skip, penalize,
     * or cancel per deferred response. Only applies to a deferred audience miss; the
     * local audience check runs before the deferred call and cannot see it.
     */
    val missBehavior: AutomationAudience.MissBehavior? = null,

    /**
     * AI suppression config resolved server side. When present it replaces the schedule's
     * `ai_suppression`, letting the deferred response add suppression to a schedule that has
     * none, or tailor the condition per resolution.
     */
    val aiSuppression: AutomationAiSuppression? = null
) {
    companion object {
        private const val IS_AUDIENCE_MATCH = "audience_match"
        private const val MESSAGE = "message"
        private const val ACTIONS = "actions"
        private const val MISS_BEHAVIOR = "miss_behavior"
        private const val AI_SUPPRESSION = "ai_suppression"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): DeferredScheduleResult {
            val content = value.requireMap()
            return DeferredScheduleResult(
                isAudienceMatch = content.requireField(IS_AUDIENCE_MATCH),
                message = content[MESSAGE]?.let(InAppMessage::parseJson),
                actions = content[ACTIONS],
                missBehavior = content[MISS_BEHAVIOR]?.let(AutomationAudience.MissBehavior::fromJson),
                aiSuppression = content[AI_SUPPRESSION]?.let(AutomationAiSuppression::fromJson)
            )
        }
    }
}
