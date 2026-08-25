/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public sealed class BannerAnimation(
    public val type: BannerAnimationType
) : JsonSerializable {

    public abstract val animateIn: Duration?
    public abstract val animateOut: Duration?

    public data class Fade(
        override val animateIn: Duration? = null,
        override val animateOut: Duration? = null
    ) : BannerAnimation(BannerAnimationType.FADE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateIn?.inWholeMilliseconds?.div(1000.0),
            ANIMATE_OUT to animateOut?.inWholeMilliseconds?.div(1000.0)
        ).toJsonValue()
    }

    public data class Slide(
        override val animateIn: Duration? = null,
        override val animateOut: Duration? = null
    ) : BannerAnimation(BannerAnimationType.SLIDE) {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            ANIMATE_IN to animateIn?.inWholeMilliseconds?.div(1000.0),
            ANIMATE_OUT to animateOut?.inWholeMilliseconds?.div(1000.0),
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
                    animateIn = content[ANIMATE_IN]?.getDouble(0.0)?.seconds,
                    animateOut = content[ANIMATE_OUT]?.getDouble(0.0)?.seconds
                )
                BannerAnimationType.SLIDE -> Slide(
                    animateIn = content[ANIMATE_IN]?.getDouble(0.0)?.seconds,
                    animateOut = content[ANIMATE_OUT]?.getDouble(0.0)?.seconds
                )
            }
        }
    }
}
