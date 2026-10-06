/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField

internal class Gradient constructor(
    @JvmField val style: Style,
    @JvmField val angle: Float?,
    @JvmField val stops: List<Stop>
) {

    enum class Style(private val value: String) {
        LINEAR("linear"),
        RADIAL("radial");

        companion object {

            @Throws(JsonException::class)
            fun from(value: JsonValue): Style {
                val content = value.requireString().lowercase()
                return entries.firstOrNull { it.value == content }
                    ?: throw JsonException("Unknown Gradient.Style value: $value")
            }
        }
    }

    class Stop constructor(
        @JvmField val color: Color,
        /** 0 - 1, along the gradient. */
        @JvmField val position: Float
    ) {

        companion object {
            private const val KEY_COLOR = "color"
            private const val KEY_POSITION = "position"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Stop {
                val content = json.requireMap()

                return Stop(
                    color = Color.fromJson(content.require(KEY_COLOR)),
                    position = content.require(KEY_POSITION).getDouble(0.0).toFloat()
                )
            }
        }
    }

    companion object {
        private const val KEY_STYLE = "style"
        private const val KEY_ANGLE = "angle"
        private const val KEY_STOPS = "stops"

        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): Gradient {
            val content = json.requireMap()

            return Gradient(
                style = Style.from(content.require(KEY_STYLE)),
                angle = content.optionalField<Double>(KEY_ANGLE)?.toFloat(),
                stops = content.require(KEY_STOPS).optList().map { Stop.fromJson(it) }
            )
        }
    }
}
