package com.urbanairship.automation.rewrite.engine.triggerprocessor

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class TriggerExecutionType(internal val value: String) {
    EXECUTION("execution"),
    DELAY_CANCELLATION("delay_cancellation");

    internal companion object {
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggerExecutionType {
            val content = value.requireString()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Invalid trigger execution type $content")
        }
    }
}
