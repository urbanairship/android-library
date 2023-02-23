package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.util.optionalField
import com.urbanairship.json.JsonMap

internal class Video(
    val aspectRatio: Double?,
    val videoControls: Boolean,
    val videoAutoplay: Boolean,
    val videoMuted: Boolean,
    val videoLoop: Boolean) {

    companion object {
        fun fromJson(json: JsonMap): Video {
            val aspectRatio: Double? = json.optionalField<Double>("aspect_ratio")
            val videoControls: Boolean = json.optionalField<Boolean>("video_controls") ?: true
            val videoAutoplay: Boolean = json.optionalField<Boolean>("video_autoplay") ?: false
            val videoMuted: Boolean = json.optionalField<Boolean>("video_muted") ?: false
            val videoLoop: Boolean = json.optionalField<Boolean>("video_loop") ?: false

            return Video(aspectRatio, videoControls, videoAutoplay, videoMuted, videoLoop)
        }
    }
}