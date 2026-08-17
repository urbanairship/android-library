/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.RestrictTo
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireEpochMillis
import java.time.Instant

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class TriggeringInfo(
    val context: DeferredTriggerContext?,
    val date: Instant,
    /**
     * ID of the execution-causing trigger, if known. Carried through so the
     * ledger can stamp the trigger that started an attempt onto its events.
     * Optional for backwards compatibility with data persisted before it
     * existed.
     */
    val triggerId: String? = null
) : JsonSerializable {
    internal companion object {
        private const val CONTEXT = "context"
        private const val DATE = "date"
        private const val TRIGGER_ID = "trigger_id"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggeringInfo {
            val content = value.requireMap()
            return TriggeringInfo(
                context = content[CONTEXT]?.let(DeferredTriggerContext::fromJson),
                date = content.requireEpochMillis(DATE),
                triggerId = content.optionalField(TRIGGER_ID)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CONTEXT to context,
        DATE to date.toEpochMilli(),
        TRIGGER_ID to triggerId
    ).toJsonValue()
}
