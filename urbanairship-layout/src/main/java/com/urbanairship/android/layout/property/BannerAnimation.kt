/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What a banner draws for one direction of its animation. Same pattern as [ModalAnimationEffect],
 * but slide has no edge of its own -- it's always the banner's own placement edge, so there's no
 * sensible independent value to give it.
 */
public sealed class BannerAnimationEffect(
    public val type: Type
) : JsonSerializable {

    public abstract val duration: Duration?

    public data class Fade(
        override val duration: Duration? = null
    ) : BannerAnimationEffect(Type.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds?.div(1000.0)
        ).toJsonValue()
    }

    public data class Slide(
        override val duration: Duration? = null
    ) : BannerAnimationEffect(Type.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds?.div(1000.0)
        ).toJsonValue()
    }

    public enum class Type(public val json: String) : JsonSerializable {
        FADE("fade"),
        SLIDE("slide");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

        public companion object {

            @Throws(JsonException::class)
            public fun fromJson(value: JsonValue): Type {
                val content = value.requireString()

                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Unknown BannerAnimationEffect type: $content")
            }
        }
    }

    public companion object {
        private const val TYPE = "type"
        private const val DURATION = "duration_seconds"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): BannerAnimationEffect {
            val content = value.requireMap()
            val duration = content[DURATION]?.getDouble(0.0)?.seconds

            return when (Type.fromJson(content.require(TYPE))) {
                Type.FADE -> Fade(duration = duration)
                Type.SLIDE -> Slide(duration = duration)
            }
        }
    }
}

/** A banner's own enter and exit animation. */
public data class BannerAnimation(
    val enter: BannerAnimationEffect,
    val exit: BannerAnimationEffect
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IN to enter,
        OUT to exit
    ).toJsonValue()

    public companion object {
        private const val IN = "in"
        private const val OUT = "out"

        /** A missing payload animation, defaulting to a slide both ways. */
        public fun default(): BannerAnimation = BannerAnimation(
            enter = BannerAnimationEffect.Slide(),
            exit = BannerAnimationEffect.Slide()
        )

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): BannerAnimation {
            val content = value.requireMap()

            return BannerAnimation(
                enter = BannerAnimationEffect.fromJson(content.require(IN)),
                exit = BannerAnimationEffect.fromJson(content.require(OUT))
            )
        }
    }
}
