package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

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
            val showControls: Boolean = json.optionalField<Boolean>("show_controls") ?: true
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
                showControls = true,
                autoplay = false,
                muted = false,
                loop = false,
                autoResetPosition = false
            )
        }
    }
}
