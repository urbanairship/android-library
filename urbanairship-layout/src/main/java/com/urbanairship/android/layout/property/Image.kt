/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.ShapeDrawableWrapper
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField

public sealed class Image(
    @JvmField public val type: Type
) {
    public enum class Type(private val value: String) {
        URL("url"),
        ICON("icon");

        public companion object {

            @Throws(JsonException::class)
            public fun from(value: JsonValue): Type {
                val content = value.requireString().lowercase()
                return entries.firstOrNull { it.value == content }
                    ?: throw JsonException("Unknown button image type value: $value")
            }
        }
    }

    public class Url public constructor(
        @JvmField public val url: String,
        public val mediaFit: MediaFit?,
        public val position: Position?
    ) : Image(Type.URL) {

        internal companion object {
            private const val KEY_URL = "url"
            private const val KEY_MEDIA_FIT = "media_fit"
            private const val KEY_POSITION = "position"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Url {
                val content = json.requireMap()

                val mediaFit = try {
                    content[KEY_MEDIA_FIT]?.let(MediaFit::from)
                } catch (_ : JsonException) {
                    null
                }

                val position = try {
                    content[KEY_POSITION]?.let(Position::fromJson)
                } catch (_ : JsonException) {
                    null
                }

                return Url(
                    url = content.optionalField(KEY_URL) ?: "",
                    mediaFit = mediaFit,
                    position = position
                )
            }
        }
    }

    public class Icon private constructor(
        private val drawable: DrawableResource,
        public val tint: Color,
        public val scale: Float
    ) : Image(Type.ICON) {

        @DrawableRes
        public fun getDrawableRes(): Int {
            return drawable.resId
        }

        public fun getDrawable(context: Context, enabledState: Boolean): Drawable? {
            return getDrawable(context, enabledState, null)
        }

        public fun getDrawable(
            context: Context, enabledState: Boolean, gravityPosition: HorizontalPosition?
        ): Drawable? {
            val d = ContextCompat.getDrawable(context, getDrawableRes()) ?: return null

            val stateTint = if (enabledState) {
                tint.resolve(context)
            } else {
                LayoutUtils.generateDisabledColor(tint.resolve(context))
            }

            DrawableCompat.setTint(d, stateTint)
            if (d is AnimatedVectorDrawable) {
                d.start()
            }
            return ShapeDrawableWrapper(d, 1f, scale, gravityPosition)
        }

        private enum class DrawableResource(
            private val value: String,
            @field:DrawableRes val resId: Int
        ) {

            CLOSE("close", R.drawable.ua_layout_ic_close),
            CHECKMARK("checkmark", R.drawable.ua_layout_ic_check),
            ARROW_FORWARD("forward_arrow", R.drawable.ua_layout_ic_arrow_forward),
            ARROW_BACK("back_arrow", R.drawable.ua_layout_ic_arrow_back),
            ERROR_CIRCLE("exclamationmark_circle_fill", R.drawable.ua_layout_ic_error_circle_filled),
            ASTERISK("asterisk", R.drawable.ua_layout_ic_asterisk),
            ASTERISK_CIRCLE("asterisk_circle_fill", R.drawable.ua_layout_ic_asterisk_circle_filled),
            STAR("star", R.drawable.ua_layout_ic_star),
            STAR_FILL("star_fill", R.drawable.ua_layout_ic_star_fill),
            HEART("heart", R.drawable.ua_layout_ic_heart),
            HEART_FILL("heart_fill", R.drawable.ua_layout_ic_heart_fill),
            PROGRESS_SPINNER("progress_spinner", R.drawable.ua_layout_animated_progress_spinner);

            companion object {

                @Throws(JsonException::class)
                fun from(value: JsonValue): DrawableResource {
                    val content = value.requireString().lowercase()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown icon drawable resource: $value")
                }
            }
        }

        public companion object {
            private const val KEY_ICON = "icon"
            private const val KEY_COLOR = "color"
            private const val KEY_SCALE = "scale"

            @Throws(JsonException::class)
            public fun fromJson(json: JsonValue): Icon {
                val content = json.requireMap()

                return Icon(
                    drawable = DrawableResource.from(content.require(KEY_ICON)),
                    tint = Color.fromJson(content.require(KEY_COLOR)),
                    scale = content.optionalField(KEY_SCALE) ?: 1f
                )
            }
        }
    }

    /**
     * Centered image span.
     */
    public class CenteredImageSpan public constructor(drawable: Drawable) : ImageSpan(drawable) {

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            canvas.save()
            val drawable = getDrawable()
            val dy = bottom - drawable.bounds.bottom - paint.fontMetricsInt.descent / 2
            canvas.translate(x, dy.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Image {
            val content = json.requireMap()
            val type = Type.from(content.require("type"))

            return when(type) {
                Type.URL -> Url.fromJson(json)
                Type.ICON -> Icon.fromJson(json)
            }
        }
    }
}
