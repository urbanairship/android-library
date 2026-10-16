/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField

/**
 * A layer painted behind a view's own content -- see `HasBackgrounds`.
 *
 * A layer never affects the view's own resolved size: the view sizes itself first, using its
 * normal rules, and only afterward is a background layer laid into the resulting box.
 */
internal sealed class Background(
    @JvmField val blendMode: BlendMode?,
    /** 0 - 1, opaque where it is left out. */
    @JvmField val opacity: Float?
) {

    enum class Type(private val value: String) {
        COLOR("color"),
        GRADIENT("gradient"),
        MEDIA("media");

        companion object {

            @Throws(JsonException::class)
            fun from(value: JsonValue): Type {
                val content = value.requireString().lowercase()
                return entries.firstOrNull { it.value == content }
                    ?: throw JsonException("Unknown Background type: $value")
            }
        }
    }

    class ColorBackground constructor(
        @JvmField val color: Color,
        blendMode: BlendMode?,
        opacity: Float?
    ) : Background(blendMode, opacity) {

        companion object {
            private const val KEY_COLOR = "color"

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap, blendMode: BlendMode?, opacity: Float?): ColorBackground {
                return ColorBackground(
                    color = Color.fromJson(json.require(KEY_COLOR)),
                    blendMode = blendMode,
                    opacity = opacity
                )
            }
        }
    }

    class GradientBackground constructor(
        @JvmField val gradient: Gradient,
        blendMode: BlendMode?,
        opacity: Float?
    ) : Background(blendMode, opacity) {

        companion object {
            private const val KEY_GRADIENT = "gradient"

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap, blendMode: BlendMode?, opacity: Float?): GradientBackground {
                return GradientBackground(
                    gradient = Gradient.fromJson(json.require(KEY_GRADIENT)),
                    blendMode = blendMode,
                    opacity = opacity
                )
            }
        }
    }

    sealed class MediaBackground(
        /**
         * This layer as a `media` view's own info.
         *
         * A `media`-type background and a `media` view state the same
         * `media_type`/`url`/`url_selectors`/`media_fit`/`position`/`video`/`view_overrides`
         * fields, so this is that same parse, done here with the rest of the layer rather than by
         * holding the layer's raw JSON to re-parse at render time. It is also what a video layer
         * hands to the `MediaModel`/`MediaView` that plays it, so a layer and an in-flow view
         * resolve their url, position and overrides through one implementation.
         */
        @JvmField val mediaInfo: MediaInfo,
        blendMode: BlendMode?,
        opacity: Float?
    ) : Background(blendMode, opacity) {

        val mediaType: MediaType get() = mediaInfo.mediaType
        val mediaFit: MediaFit get() = mediaInfo.mediaFit
        val position: Position get() = mediaInfo.position

        class ImageMediaBackground(
            mediaInfo: MediaInfo,
            blendMode: BlendMode?,
            opacity: Float?
        ) : MediaBackground(mediaInfo, blendMode, opacity)

        /**
         * A layer whose media has a playback surface, so [video] means something on it.
         *
         * An image layer is [ImageMediaBackground] instead, without the field: autoplay, mute,
         * loop and controls have nothing to act on there, and one flat type carrying [video]
         * regardless let a scene set it on an image and be silently ignored.
         */
        class VideoMediaBackground(
            mediaInfo: MediaInfo,
            blendMode: BlendMode?,
            opacity: Float?
        ) : MediaBackground(mediaInfo, blendMode, opacity) {
            /** Playback options, which only a layer with a playback surface can carry. */
            val video: Video? get() = mediaInfo.video
        }

        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap, blendMode: BlendMode?, opacity: Float?): MediaBackground {
                val mediaInfo = MediaInfo(json)
                return if (mediaInfo.mediaType.isPlayable) {
                    VideoMediaBackground(mediaInfo, blendMode, opacity)
                } else {
                    ImageMediaBackground(mediaInfo, blendMode, opacity)
                }
            }
        }
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_BLEND_MODE = "blend_mode"
        private const val KEY_OPACITY = "opacity"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): Background {
            val blendMode = json[KEY_BLEND_MODE]?.let(BlendMode::from)
            val opacity = json.optionalField<Double>(KEY_OPACITY)?.toFloat()

            return when (Type.from(json.require(KEY_TYPE))) {
                Type.COLOR -> ColorBackground.fromJson(json, blendMode, opacity)
                Type.GRADIENT -> GradientBackground.fromJson(json, blendMode, opacity)
                Type.MEDIA -> MediaBackground.fromJson(json, blendMode, opacity)
            }
        }

        @Throws(JsonException::class)
        fun fromJsonList(json: JsonList): List<Background> = json.map { fromJson(it.requireMap()) }
    }
}
