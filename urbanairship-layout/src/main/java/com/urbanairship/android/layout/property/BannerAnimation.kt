/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public sealed class BannerAnimation(
    public val type: BannerAnimationType
) : JsonSerializable {

    public data class Fade(
        val animateInSeconds: Double? = null,
        val animateOutSeconds: Double? = null
    ) : BannerAnimation(BannerAnimationType.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateInSeconds,
            ANIMATE_OUT to animateOutSeconds
        ).toJsonValue()
    }

    public data class Slide(
        val animateInSeconds: Double? = null,
        val animateOutSeconds: Double? = null
    ) : BannerAnimation(BannerAnimationType.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateInSeconds,
            ANIMATE_OUT to animateOutSeconds,
        ).toJsonValue()
    }

    public enum class BannerAnimationType(public val json: String) : JsonSerializable {
        FADE("fade"),
        SLIDE("slide");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

        public companion object {

            @Throws(JsonException::class)
            public fun fromJson(value: JsonValue): BannerAnimationType {
                val content = value.requireString()

                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Unknown BannerAnimationType value: $content")
            }
        }
    }

    public companion object {
        private const val TYPE = "type"
        private const val ANIMATE_IN = "animate_in_seconds"
        private const val ANIMATE_OUT = "animate_out_seconds"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): BannerAnimation {
            val content = value.requireMap()

            return when (BannerAnimationType.fromJson(content.require(TYPE))) {
                BannerAnimationType.FADE -> Fade(
                    animateInSeconds = content[ANIMATE_IN]?.getDouble(0.0),
                    animateOutSeconds = content[ANIMATE_OUT]?.getDouble(0.0)
                )
                BannerAnimationType.SLIDE -> Slide(
                    animateInSeconds = content[ANIMATE_IN]?.getDouble(0.0),
                    animateOutSeconds = content[ANIMATE_OUT]?.getDouble(0.0)
                )
            }
        }
    }
}
