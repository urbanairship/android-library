package com.urbanairship.automation.rewrite.inappmessage.analytics

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal sealed class InAppEventMessageID : JsonSerializable {
    companion object {
        private const val MESSAGE_ID = "message_id"
        private const val CAMPAIGNS = "campaigns"
    }

    abstract val identifier: String

    data class Legacy(override val identifier: String) : InAppEventMessageID() {
        override fun toJsonValue(): JsonValue = JsonValue.wrap(identifier)
    }

    data class AppDefined(override val identifier: String) : InAppEventMessageID() {
        override fun toJsonValue(): JsonValue = jsonMapOf(MESSAGE_ID to identifier).toJsonValue()
    }

    data class AirshipID(
        override val identifier: String,
        val campaigns: JsonValue?
    ) : InAppEventMessageID() {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            MESSAGE_ID to identifier,
            CAMPAIGNS to campaigns
        ).toJsonValue()
    }
}
