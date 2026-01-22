package com.urbanairship.android.layout.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class LayoutEventMessageId : JsonSerializable {
    private companion object {
        private const val MESSAGE_ID = "message_id"
        private const val CAMPAIGNS = "campaigns"
    }

    public abstract val identifier: String

    public data class Legacy(override val identifier: String) : LayoutEventMessageId() {
        override fun toJsonValue(): JsonValue = JsonValue.Companion.wrap(identifier)
    }

    public data class AppDefined(override val identifier: String) : LayoutEventMessageId() {
        override fun toJsonValue(): JsonValue = jsonMapOf(MESSAGE_ID to identifier).toJsonValue()
    }

    public data class AirshipId(
        override val identifier: String,
        val campaigns: JsonValue?
    ) : LayoutEventMessageId() {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            MESSAGE_ID to identifier, CAMPAIGNS to campaigns
        ).toJsonValue()
    }
}
