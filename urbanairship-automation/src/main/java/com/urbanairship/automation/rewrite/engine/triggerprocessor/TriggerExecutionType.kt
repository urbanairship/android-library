package com.urbanairship.automation.rewrite.engine.triggerprocessor

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

internal enum class TriggerExecutionType(val value: String) {
    EXECUTION("execution"),
    DELAY_CANCELLATION("delay_cancellation");

    companion object {
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggerExecutionType {
            val content = value.requireString()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Invalid trigger execution type $content")
        }
    }
}
