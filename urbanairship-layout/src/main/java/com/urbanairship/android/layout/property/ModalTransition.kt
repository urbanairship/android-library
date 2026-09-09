/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What a modal draws for one direction of its transition. An effect only ever plays its own
 * direction, so its own [duration] lives here rather than on a wrapper as an
 * animateIn/animateOut pair that in/out would otherwise have to be cross-referenced against by
 * name.
 */
public sealed class ModalTransitionEffect(
    public val type: Type
) : JsonSerializable {

    public abstract val duration: Duration?

    /** Fades in or out using opacity. */
    public data class Fade(
        override val duration: Duration? = null
    ) : ModalTransitionEffect(Type.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds?.div(1000.0)
        ).toJsonValue()
    }

    /** Slides in or out from the given edge. */
    public data class Slide(
        val edge: EdgePosition,
        override val duration: Duration? = null
    ) : ModalTransitionEffect(Type.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds?.div(1000.0),
            EDGE to edge
        ).toJsonValue()
    }

    /** Scales in or out from the given corner. */
    public data class Explode(
        val corner: CornerPosition,
        override val duration: Duration? = null
    ) : ModalTransitionEffect(Type.EXPLODE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            DURATION to duration?.inWholeMilliseconds?.div(1000.0),
            CORNER to corner
        ).toJsonValue()
    }

    public enum class Type(public val json: String) : JsonSerializable {
        FADE("fade"),
        SLIDE("slide"),
        EXPLODE("explode");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

        public companion object {

            @Throws(JsonException::class)
            public fun fromJson(value: JsonValue): Type {
                val content = value.requireString()

                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Unknown ModalTransitionEffect type: $content")
            }
        }
    }

    public companion object {
        private const val TYPE = "type"
        private const val DURATION = "duration_seconds"
        private const val EDGE = "edge"
        private const val CORNER = "corner"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): ModalTransitionEffect {
            val content = value.requireMap()
            val duration = content[DURATION]?.getDouble(0.0)?.seconds

            return when (Type.fromJson(content.require(TYPE))) {
                Type.FADE -> Fade(duration = duration)
                Type.SLIDE -> Slide(
                    edge = EdgePosition.fromJson(content.require(EDGE)),
                    duration = duration
                )
                Type.EXPLODE -> Explode(
                    corner = CornerPosition.fromJson(content.require(CORNER)),
                    duration = duration
                )
            }
        }
    }
}

/**
 * A modal's own enter and exit transition. A plain transition and one whose entrance and exit are
 * different effects entirely (explode in, fade out) are both just this, played twice, rather than
 * a symmetric case with a separate "asymmetric" one bolted on beside it.
 */
public data class ModalTransition(
    val enter: ModalTransitionEffect,
    val exit: ModalTransitionEffect
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IN to enter,
        OUT to exit
    ).toJsonValue()

    public companion object {
        private const val IN = "in"
        private const val OUT = "out"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): ModalTransition {
            val content = value.requireMap()

            return ModalTransition(
                enter = ModalTransitionEffect.fromJson(content.require(IN)),
                exit = ModalTransitionEffect.fromJson(content.require(OUT))
            )
        }
    }
}
