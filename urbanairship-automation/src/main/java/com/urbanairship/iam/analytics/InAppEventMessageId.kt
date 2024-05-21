/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal sealed class InAppEventMessageId : JsonSerializable {
    companion object {
        private const val MESSAGE_ID = "message_id"
        private const val CAMPAIGNS = "campaigns"
    }

    abstract val identifier: String

    data class Legacy(override val identifier: String) : InAppEventMessageId() {
        override fun toJsonValue(): JsonValue = JsonValue.wrap(identifier)
    }

    data class AppDefined(override val identifier: String) : InAppEventMessageId() {
        override fun toJsonValue(): JsonValue = jsonMapOf(MESSAGE_ID to identifier).toJsonValue()
    }

    data class AirshipId(
        override val identifier: String,
        val campaigns: JsonValue?
    ) : InAppEventMessageId() {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            MESSAGE_ID to identifier,
            CAMPAIGNS to campaigns
        ).toJsonValue()
    }
}
