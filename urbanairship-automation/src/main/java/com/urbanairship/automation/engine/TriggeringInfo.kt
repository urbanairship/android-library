/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.RestrictTo
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class TriggeringInfo(
    val context: DeferredTriggerContext?,
    val date: Long
) : JsonSerializable {
    internal companion object {
        private const val CONTEXT = "context"
        private const val DATE = "date"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggeringInfo {
            val content = value.requireMap()
            return TriggeringInfo(
                context = content[CONTEXT]?.let(DeferredTriggerContext::fromJson),
                date = content.requireField(DATE)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CONTEXT to context,
        DATE to date
    ).toJsonValue()
}
