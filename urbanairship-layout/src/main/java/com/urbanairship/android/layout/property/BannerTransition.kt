/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What a banner draws for one direction of its transition. Same pattern as [ModalTransitionEffect],
 * but slide has no edge of its own -- it's always the banner's own placement edge, so there's no
 * sensible independent value to give it.
 */
public sealed class BannerTransitionEffect(
    public val type: Type
) : JsonSerializable {

    public abstract val duration: Duration?

    public data class Fade(
        override val duration: Duration? = null
    ) : BannerTransitionEffect(Type.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds
        ).toJsonValue()
    }

    public data class Slide(
        override val duration: Duration? = null
    ) : BannerTransitionEffect(Type.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds
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
                    ?: throw JsonException("Unknown BannerTransitionEffect type: $content")
            }
        }
    }

    public companion object {
        private const val TYPE = "type"
        private const val DURATION = "duration_milliseconds"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): BannerTransitionEffect {
            val content = value.requireMap()
            val duration = content[DURATION]?.getDouble(0.0)?.milliseconds

            return when (Type.fromJson(content.require(TYPE))) {
                Type.FADE -> Fade(duration = duration)
                Type.SLIDE -> Slide(duration = duration)
            }
        }
    }
}

/** A banner's own enter and exit transition. */
public data class BannerTransition(
    val enter: BannerTransitionEffect,
    val exit: BannerTransitionEffect
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IN to enter,
        OUT to exit
    ).toJsonValue()

    public companion object {
        private const val IN = "in"
        private const val OUT = "out"

        /** A missing payload transition, defaulting to a slide both ways. */
        public fun default(): BannerTransition = BannerTransition(
            enter = BannerTransitionEffect.Slide(),
            exit = BannerTransitionEffect.Slide()
        )

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): BannerTransition {
            val content = value.requireMap()

            return BannerTransition(
                enter = BannerTransitionEffect.fromJson(content.require(IN)),
                exit = BannerTransitionEffect.fromJson(content.require(OUT))
            )
        }
    }
}
