/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.shape

import android.R
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Color.Companion.fromJsonField
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.widget.ShapeDrawableWrapper
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/**
 * Representation of a Shape.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Shape public constructor(
    public val type: ShapeType,
    public val aspectRatio: Float,
    public val scale: Float,
    public val border: Border?,
    public val color: Color?
) {

    public fun getDrawable(context: Context): Drawable {
        return getDrawable(context, true)
    }

    public fun getDrawable(context: Context, enabledState: Boolean): Drawable {
        @ColorInt val drawableColor = color?.resolve(context) ?: Color.TRANSPARENT
        val strokeWidth = border?.strokeWidth
            ?.let { ResourceUtils.dpToPx(context, it) }
            ?.toInt()
            ?: 0

        @ColorInt val strokeColor = border?.strokeColor?.resolve(context) ?: 0

        val drawable = GradientDrawable()
        drawable.shape = type.drawableShape
        drawable.setColor(
            if (enabledState) drawableColor
            else LayoutUtils.generateDisabledColor(drawableColor)
        )
        drawable.setStroke(
            strokeWidth,
            if (enabledState) strokeColor
            else LayoutUtils.generateDisabledColor(strokeColor)
        )

        val radii = border?.radii { ResourceUtils.dpToPx(context, it) }
        if (radii != null) {
            drawable.cornerRadii = radii
        } else {
            drawable.cornerRadius = 0f
        }

        return ShapeDrawableWrapper(drawable, aspectRatio, scale, null)
    }

    public companion object {

        private val CHECKED_STATE_SET = intArrayOf(R.attr.state_checked)
        private val DISABLED_CHECKED_STATE_SET =
            intArrayOf(-R.attr.state_enabled, R.attr.state_checked)
        private val DISABLED_UNCHECKED_STATE_SET =
            intArrayOf(-R.attr.state_enabled, -R.attr.state_checked)
        private val EMPTY_STATE_SET: IntArray? = StateSet.NOTHING

        private const val KEY_TYPE = "type"
        private const val KEY_ASPECT_RATIO = "aspect_ratio"
        private const val KEY_SCALE = "scale"
        private const val KEY_BORDER = "border"
        private const val KEY_COLOR = "color"


        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Shape {
            val content = json.requireMap()

            return Shape(
                type = ShapeType.from(content.require(KEY_TYPE)),
                aspectRatio = content.optionalField(KEY_ASPECT_RATIO) ?: 1f,
                scale = content.optionalField(KEY_SCALE) ?: 1f,
                border = content[KEY_BORDER]?.let(Border::fromJson),
                color = content[KEY_COLOR]?.let(Color::fromJson)
            )

        }

        public fun buildStateListDrawable(
            context: Context,
            checkedShapes: List<Shape>,
            uncheckedShapes: List<Shape>,
            checkedIcon: Image.Icon?,
            uncheckedIcon: Image.Icon?
        ): StateListDrawable {
            // Build layer drawables from checked shapes/icons
            val checkedDrawable: LayerDrawable =
                buildLayerDrawable(context, checkedShapes, checkedIcon, true)
            val disabledCheckedDrawable: LayerDrawable =
                buildLayerDrawable(context, checkedShapes, checkedIcon, false)

            // Build layer drawables from unchecked shapes/icons
            val uncheckedDrawable: LayerDrawable =
                buildLayerDrawable(context, uncheckedShapes, uncheckedIcon, true)
            val disabledUncheckedDrawable: LayerDrawable =
                buildLayerDrawable(context, uncheckedShapes, uncheckedIcon, false)

            // Combine layer drawables into a single state list drawable
            val drawable = StateListDrawable()
            drawable.addState(DISABLED_CHECKED_STATE_SET, disabledCheckedDrawable)
            drawable.addState(DISABLED_UNCHECKED_STATE_SET, disabledUncheckedDrawable)
            drawable.addState(CHECKED_STATE_SET, checkedDrawable)
            drawable.addState(EMPTY_STATE_SET, uncheckedDrawable)

            return drawable
        }

        internal fun buildLayerDrawable(
            context: Context, shapes: List<Shape>, icon: Image.Icon?, enabledState: Boolean
        ): LayerDrawable {
            val layerCount = shapes.size + (if (icon != null) 1 else 0)
            val layers = arrayOfNulls<Drawable>(layerCount)
            for (i in shapes.indices) {
                layers[i] = shapes[i].getDrawable(context, enabledState)
            }
            if (icon != null) {
                layers[layerCount - 1] = icon.getDrawable(context, enabledState)
            }
            return LayerDrawable(layers)
        }
    }
}
