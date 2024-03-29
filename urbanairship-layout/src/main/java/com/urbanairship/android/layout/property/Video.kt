package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

internal class Video(
    val aspectRatio: Double?,
    val showControls: Boolean,
    val autoplay: Boolean,
    val muted: Boolean,
    val loop: Boolean
) {

    companion object {
        fun fromJson(json: JsonMap): Video {
            val aspectRatio: Double? = json.optionalField<Double>("aspect_ratio")
            val showControls: Boolean = json.optionalField<Boolean>("show_controls") ?: true
            val autoplay: Boolean = json.optionalField<Boolean>("autoplay") ?: false
            val muted: Boolean = json.optionalField<Boolean>("muted") ?: false
            val loop: Boolean = json.optionalField<Boolean>("loop") ?: false

            return Video(aspectRatio, showControls, autoplay, muted, loop)
        }

        fun defaultVideo(): Video {
            val showControls = true
            val autoplay = false
            val muted = false
            val loop = false

            return Video(null, showControls, autoplay, muted, loop)
        }
    }
}
