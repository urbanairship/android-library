/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * How far a [ParallaxBackground] layer moves relative to the scroll or drag position of the
 * `pager`/`scroll_layout` that holds it.
 *
 * [factor] `0` pins the layer in place; `1` moves it in lockstep with the content (no visible
 * parallax); negative reverses direction.
 */
internal class Parallax constructor(
    @JvmField val axis: Axis,
    @JvmField val factor: Float
) {

    enum class Axis(private val value: String) {
        VERTICAL("vertical"),
        HORIZONTAL("horizontal"),
        BOTH("both");

        companion object {

            @Throws(JsonException::class)
            fun from(value: JsonValue): Axis {
                val content = value.requireString().lowercase()
                return entries.firstOrNull { it.value == content }
                    ?: throw JsonException("Unknown Parallax.Axis value: $value")
            }
        }
    }

    companion object {
        private const val KEY_AXIS = "axis"
        private const val KEY_FACTOR = "factor"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): Parallax {
            return Parallax(
                axis = Axis.from(json.require(KEY_AXIS)),
                factor = json.require(KEY_FACTOR).getDouble(0.0).toFloat()
            )
        }
    }
}
