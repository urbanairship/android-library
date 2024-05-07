package com.urbanairship.automation.deferred

import com.urbanairship.iam.InAppMessage
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField

internal data class DeferredScheduleResult(
    val isAudienceMatch: Boolean,
    val message: InAppMessage? = null,
    val actions: JsonValue? = null
) {
    companion object {
        private const val IS_AUDIENCE_MATCH = "audience_match"
        private const val MESSAGE = "message"
        private const val ACTIONS = "actions"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): DeferredScheduleResult {
            val content = value.requireMap()
            return DeferredScheduleResult(
                isAudienceMatch = content.requireField(IS_AUDIENCE_MATCH),
                message = content.get(MESSAGE)?.let(InAppMessage::parseJson),
                actions = content.get(ACTIONS)
            )
        }
    }
}
