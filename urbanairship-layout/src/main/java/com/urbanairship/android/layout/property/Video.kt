package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

/**
 * A `media` view's or `backgrounds` layer's playback options.
 *
 * Every field means the same thing and defaults the same way wherever it appears: in-flow or
 * behind content, and on every platform. In particular a video shows no controls unless the scene
 * asks for them -- the common case is decorative, and a scene that wants a player says so.
 */
internal class Video(
    val aspectRatio: Double?,
    val showControls: Boolean,
    val autoplay: Boolean,
    val muted: Boolean,
    val loop: Boolean,
    val autoResetPosition: Boolean
) {

    companion object {
        fun fromJson(json: JsonMap): Video {
            val aspectRatio: Double? = json.optionalField<Double>("aspect_ratio")
            val showControls: Boolean = json.optionalField<Boolean>("show_controls") ?: false
            val autoplay: Boolean = json.optionalField<Boolean>("autoplay") ?: false
            val muted: Boolean = json.optionalField<Boolean>("muted") ?: false
            val loop: Boolean = json.optionalField<Boolean>("loop") ?: false
            val autoResetPosition: Boolean = json.optionalField<Boolean>("auto_reset_position")
                ?: (autoplay && !showControls)

            return Video(aspectRatio, showControls, autoplay, muted, loop, autoResetPosition)
        }

        fun defaultVideo(): Video {
            return Video(
                aspectRatio = null,
                showControls = false,
                autoplay = false,
                muted = false,
                loop = false,
                autoResetPosition = false
            )
        }
    }
}
