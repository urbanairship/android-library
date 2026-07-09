/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField

/**
 * Banner enter/exit animation.
 *
 * Fades in and out using opacity, or slides in and out from the banner's placement edge.
 */
public sealed class BannerAnimation {
    public abstract val animateInMs: Long
    public abstract val animateOutMs: Long

    public data class Fade(
        override val animateInMs: Long,
        override val animateOutMs: Long
    ) : BannerAnimation()

    public data class Slide(
        override val animateInMs: Long,
        override val animateOutMs: Long
    ) : BannerAnimation()

    public companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_ANIMATE_IN_SECONDS = "animate_in_seconds"
        private const val KEY_ANIMATE_OUT_SECONDS = "animate_out_seconds"

        private const val TYPE_FADE = "fade"
        private const val TYPE_SLIDE = "slide"

        /** Default animation duration, if not specified in the payload. */
        public const val DEFAULT_ANIMATION_MS: Long = 300L

        /** Default animation, if not specified in the payload. */
        public val DEFAULT: BannerAnimation = Slide(DEFAULT_ANIMATION_MS, DEFAULT_ANIMATION_MS)

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerAnimation {
            val content = json.requireMap()

            val animateInMs = content.optionalField<Double>(KEY_ANIMATE_IN_SECONDS)
                ?.let { (it * 1000).toLong() } ?: DEFAULT_ANIMATION_MS
            val animateOutMs = content.optionalField<Double>(KEY_ANIMATE_OUT_SECONDS)
                ?.let { (it * 1000).toLong() } ?: DEFAULT_ANIMATION_MS

            return when (val type = content.optionalField<String>(KEY_TYPE)?.lowercase()) {
                TYPE_FADE -> Fade(animateInMs, animateOutMs)
                TYPE_SLIDE -> Slide(animateInMs, animateOutMs)
                else -> throw JsonException("Unknown BannerAnimation type: $type")
            }
        }
    }
}
