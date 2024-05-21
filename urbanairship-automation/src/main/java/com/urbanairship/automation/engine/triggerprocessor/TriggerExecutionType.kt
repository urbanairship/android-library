/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine.triggerprocessor

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

internal enum class TriggerExecutionType(internal val value: String) {
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
