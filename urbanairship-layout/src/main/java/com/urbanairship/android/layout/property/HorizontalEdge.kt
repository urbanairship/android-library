/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class HorizontalEdge(private val value: String) {
    START("start"),
    END("end");

    public val baseType: HorizontalPosition
        get() = when (this) {
            START -> HorizontalPosition.START
            END -> HorizontalPosition.END
        }

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun from(value: JsonValue): HorizontalEdge {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown HorizontalEdge value: $value")
        }
    }
}
