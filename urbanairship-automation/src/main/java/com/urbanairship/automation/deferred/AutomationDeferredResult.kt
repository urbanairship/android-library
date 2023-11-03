package com.urbanairship.automation.deferred

import androidx.annotation.RestrictTo
import com.urbanairship.iam.InAppMessage
import com.urbanairship.json.JsonValue

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AutomationDeferredResult(
    val isAudienceMatched: Boolean,
    val inAppMessage: InAppMessage?
) {

    internal companion object {
        private const val AUDIENCE_MATCH_KEY = "audience_match"
        private const val RESPONSE_TYPE_KEY = "type"
        private const val MESSAGE_KEY = "message"
        private const val IN_APP_MESSAGE_TYPE = "in_app_message"

        @JvmStatic
        fun parse(json: JsonValue): AutomationDeferredResult {
            val map = json.optMap()
            val isMatched = map.opt(AUDIENCE_MATCH_KEY).getBoolean(false)
            val message: InAppMessage?
            if (isMatched && IN_APP_MESSAGE_TYPE == map.opt(RESPONSE_TYPE_KEY).optString()) {
                message = InAppMessage.fromJson(map.opt(MESSAGE_KEY), InAppMessage.SOURCE_REMOTE_DATA)
            } else {
                message = null
            }
            return AutomationDeferredResult(isMatched, message)
        }
    }
}
