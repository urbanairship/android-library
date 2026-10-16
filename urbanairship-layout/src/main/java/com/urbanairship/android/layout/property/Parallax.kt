/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

/**
 * How far a [ParallaxBackground] layer moves relative to the scroll or drag position of the
 * `pager`/`scroll_layout` that holds it.
 *
 * [factor] `0` pins the layer in place; `1` moves it in lockstep with the content (no visible
 * parallax); negative reverses direction.
 *
 * There is no axis to state: a host has exactly one scroll or drag position, and already knows
 * which way it runs -- a pager drags horizontally, a `scroll_layout` scrolls along its own
 * `direction`. A layer travels along that or not at all, and `factor: 0` is how it says "not at
 * all".
 */
internal class Parallax constructor(
    @JvmField val factor: Float
) {

    companion object {
        private const val KEY_FACTOR = "factor"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): Parallax =
            Parallax(factor = json.require(KEY_FACTOR).getDouble(0.0).toFloat())
    }
}
