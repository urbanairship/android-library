/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public sealed class ModalAnimation(
    public val type: ModalAnimationType
) : JsonSerializable {

    public data class Fade(
        val animateInSeconds: Double? = null,
        val animateOutSeconds: Double? = null
    ) : ModalAnimation(ModalAnimationType.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateInSeconds,
            ANIMATE_OUT to animateOutSeconds
        ).toJsonValue()
    }

    public data class Slide(
        val origin: EdgePosition,
        val animateInSeconds: Double? = null,
        val animateOutSeconds: Double? = null
    ) : ModalAnimation(ModalAnimationType.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateInSeconds,
            ANIMATE_OUT to animateOutSeconds,
            ORIGIN to origin
        ).toJsonValue()
    }

    public data class Explode(
        val enter: CornerPosition,
        val exit: CornerPosition,
        val animateInSeconds: Double? = null,
        val animateOutSeconds: Double? = null
    ) : ModalAnimation(ModalAnimationType.EXPLODE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateInSeconds,
            ANIMATE_OUT to animateOutSeconds,
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
                    animateInSeconds = content[ANIMATE_IN]?.getDouble(0.0),
                    animateOutSeconds = content[ANIMATE_OUT]?.getDouble(0.0)
                )
                ModalAnimationType.SLIDE -> Slide(
                    origin = EdgePosition.fromJson(content.require(ORIGIN)),
                    animateInSeconds = content[ANIMATE_IN]?.getDouble(0.0),
                    animateOutSeconds = content[ANIMATE_OUT]?.getDouble(0.0)
                )
                ModalAnimationType.EXPLODE -> Explode(
                    enter = CornerPosition.fromJson(content.require(ENTER)),
                    exit = CornerPosition.fromJson(content.require(EXIT)),
                    animateInSeconds = content[ANIMATE_IN]?.getDouble(0.0),
                    animateOutSeconds = content[ANIMATE_OUT]?.getDouble(0.0)
                )
            }
        }
    }
}
