/*
 Copyright Airship and Contributors
 */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class FormBehaviorType(
    private val value: String
) {
    SUBMIT_EVENT("submit_event");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): FormBehaviorType {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown Form Behavior Type value: $value")
        }
    }
}
