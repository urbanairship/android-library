package com.urbanairship.automation.deferred

import android.net.Uri
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Objects

internal class DeferredAutomationData internal constructor(
    internal val url: Uri,
    internal val retryOnTimeOut: Boolean?,
    internal val type: DeferredType
) : JsonSerializable {
    internal enum class DeferredType(val json: String): JsonSerializable {
        IN_APP_MESSAGE("in_app_message"),
        ACTIONS("actions");

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): DeferredType {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid deferred type $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    companion object {
        private const val URL = "url"
        private const val RETRY_ON_TIME_OUT = "retry_on_timeout"
        private const val TYPE = "type"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): DeferredAutomationData {
            val content = value.requireMap()
            return DeferredAutomationData(
                url = content.requireField<String>(URL).let(Uri::parse),
                retryOnTimeOut = content.optionalField(RETRY_ON_TIME_OUT),
                type = DeferredType.fromJson(content.require(TYPE))
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        TYPE to type,
        RETRY_ON_TIME_OUT to retryOnTimeOut,
        URL to url.toString()
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeferredAutomationData

        if (url != other.url) return false
        if (retryOnTimeOut != other.retryOnTimeOut) return false
        return type == other.type
    }

    override fun hashCode(): Int {
        return Objects.hash(url, retryOnTimeOut, type)
    }

}

internal fun DeferredAutomationData.isInAppMessage(): Boolean {
    return when(type) {
        DeferredAutomationData.DeferredType.IN_APP_MESSAGE -> true
        DeferredAutomationData.DeferredType.ACTIONS -> false
    }
}
