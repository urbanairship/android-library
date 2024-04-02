package com.urbanairship.automation.rewrite.engine

import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import kotlin.jvm.Throws

internal data class TriggeringInfo(
    val context: DeferredTriggerContext?,
    val date: Long
) : JsonSerializable {
    companion object {
        private const val CONTEXT = "context"
        private const val DATE = "date"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggeringInfo {
            val content = value.requireMap()
            return TriggeringInfo(
                context = content.get(CONTEXT)?.let(DeferredTriggerContext::fromJson),
                date = content.requireField(DATE)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CONTEXT to context,
        DATE to date
    ).toJsonValue()
}
