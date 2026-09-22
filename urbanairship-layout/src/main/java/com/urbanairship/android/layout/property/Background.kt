/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList

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

    class MediaBackground constructor(
        @JvmField val mediaType: MediaType,
        @JvmField val url: String,
        @JvmField val urlSelectors: List<MediaUrlSelector>,
        @JvmField val mediaFit: MediaFit,
        @JvmField val position: Position?,
        @JvmField val video: Video?,
        /**
         * This layer's own JSON, unchanged. A video layer's playback is rendered by a real
         * `MediaInfo`/`MediaModel`/`MediaView` -- the same classes an in-flow `media` view uses --
         * rather than reimplementing WebView/video lifecycle handling here; this is what a host
         * that renders video hands to `MediaInfo`'s constructor to get one. Its keys already match,
         * since a `media`-type background and a `media` view parse the same `media_type`/`url`/
         * `media_fit`/`position`/`video` fields.
         */
        @JvmField val rawJson: JsonMap,
        blendMode: BlendMode?,
        opacity: Float?
    ) : Background(blendMode, opacity) {

        fun resolveUrl(isDarkMode: Boolean): String =
            MediaUrlSelector.resolve(url, urlSelectors, isDarkMode)

        companion object {
            private const val KEY_MEDIA_TYPE = "media_type"
            private const val KEY_URL = "url"
            private const val KEY_URL_SELECTORS = "url_selectors"
            private const val KEY_MEDIA_FIT = "media_fit"
            private const val KEY_POSITION = "position"
            private const val KEY_VIDEO = "video"

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap, blendMode: BlendMode?, opacity: Float?): MediaBackground {
                return MediaBackground(
                    mediaType = MediaType.from(json.require(KEY_MEDIA_TYPE)),
                    url = json.require(KEY_URL).requireString(),
                    urlSelectors = json.optionalList(KEY_URL_SELECTORS)
                        ?.let(MediaUrlSelector::fromJsonList) ?: emptyList(),
                    mediaFit = MediaFit.from(json.require(KEY_MEDIA_FIT)),
                    position = json[KEY_POSITION]?.let(Position::fromJson),
                    video = json.optionalField<JsonMap>(KEY_VIDEO)?.let(Video::fromJson),
                    rawJson = json,
                    blendMode = blendMode,
                    opacity = opacity
                )
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
