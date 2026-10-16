/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalMap

/**
 * A [Background] layer that may also declare [parallax].
 *
 * Only used by view types with a scroll or drag position for parallax to read (`pager`,
 * `scroll_layout`) -- everything else uses a plain [Background], which has no `parallax` field at
 * all, rather than one that would silently do nothing.
 */
internal class ParallaxBackground constructor(
    @JvmField val background: Background,
    @JvmField val parallax: Parallax?
) {

    companion object {
        private const val KEY_PARALLAX = "parallax"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ParallaxBackground {
            return ParallaxBackground(
                background = Background.fromJson(json),
                parallax = json.optionalMap(KEY_PARALLAX)?.let(Parallax::fromJson)
            )
        }

        @Throws(JsonException::class)
        fun fromJsonList(json: JsonList): List<ParallaxBackground> = json.map { fromJson(it.requireMap()) }
    }
}
