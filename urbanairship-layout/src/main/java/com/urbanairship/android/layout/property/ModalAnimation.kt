/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public sealed class ModalAnimation(
    public val type: ModalAnimationType
) : JsonSerializable {

    public abstract val animateIn: Duration?
    public abstract val animateOut: Duration?

    public data class Fade(
        override val animateIn: Duration? = null,
        override val animateOut: Duration? = null
    ) : ModalAnimation(ModalAnimationType.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateIn?.inWholeMilliseconds?.div(1000.0),
            ANIMATE_OUT to animateOut?.inWholeMilliseconds?.div(1000.0)
        ).toJsonValue()
    }

    public data class Slide(
        val origin: EdgePosition,
        override val animateIn: Duration? = null,
        override val animateOut: Duration? = null
    ) : ModalAnimation(ModalAnimationType.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateIn?.inWholeMilliseconds?.div(1000.0),
            ANIMATE_OUT to animateOut?.inWholeMilliseconds?.div(1000.0),
            ORIGIN to origin
        ).toJsonValue()
    }

    public data class Explode(
        val enter: CornerPosition,
        val exit: CornerPosition,
        override val animateIn: Duration? = null,
        override val animateOut: Duration? = null
    ) : ModalAnimation(ModalAnimationType.EXPLODE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateIn?.inWholeMilliseconds?.div(1000.0),
            ANIMATE_OUT to animateOut?.inWholeMilliseconds?.div(1000.0),
            ENTER to enter,
            EXIT to exit
        ).toJsonValue()
    }

    public enum class ModalAnimationType(public val json: String) : JsonSerializable {
        FADE("fade"),
        SLIDE("slide"),
        EXPLODE("explode");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

        public companion object {

            @Throws(JsonException::class)
            public fun fromJson(value: JsonValue): ModalAnimationType {
                val content = value.requireString()

                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Unknown ModalAnimationType value: $content")
            }
        }
    }

    public companion object {
        private const val TYPE = "type"
        private const val ANIMATE_IN = "animate_in_seconds"
        private const val ANIMATE_OUT = "animate_out_seconds"
        private const val ORIGIN = "origin"
        private const val ENTER = "enter"
        private const val EXIT = "exit"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): ModalAnimation {
            val content = value.requireMap()

            return when (ModalAnimationType.fromJson(content.require(TYPE))) {
                ModalAnimationType.FADE -> Fade(
                    animateIn = content[ANIMATE_IN]?.getDouble(0.0)?.seconds,
                    animateOut = content[ANIMATE_OUT]?.getDouble(0.0)?.seconds
                )
                ModalAnimationType.SLIDE -> Slide(
                    origin = EdgePosition.fromJson(content.require(ORIGIN)),
                    animateIn = content[ANIMATE_IN]?.getDouble(0.0)?.seconds,
                    animateOut = content[ANIMATE_OUT]?.getDouble(0.0)?.seconds
                )
                ModalAnimationType.EXPLODE -> Explode(
                    enter = CornerPosition.fromJson(content.require(ENTER)),
                    exit = CornerPosition.fromJson(content.require(EXIT)),
                    animateIn = content[ANIMATE_IN]?.getDouble(0.0)?.seconds,
                    animateOut = content[ANIMATE_OUT]?.getDouble(0.0)?.seconds
                )
            }
        }
    }
}
