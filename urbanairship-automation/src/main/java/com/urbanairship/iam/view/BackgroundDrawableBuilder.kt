/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.Dimension.Companion.DP

/**
 * Generates a background with an optional pressed state.
 */
internal class BackgroundDrawableBuilder private constructor(
    private val context: Context
) {

    private var backgroundColor = Color.TRANSPARENT
    private var strokeColor: Int? = null
    private var pressedColor: Int? = null
    private var strokeWidthDps = 0
    private var borderRadiusDps = 0f

    @BorderRadius.BorderRadiusFlag
    private var borderRadiusFlag = 0

    /**
     * Sets the pressed color.
     *
     * @param color The pressed color.
     * @return The builder instance.
     */
    fun setPressedColor(@ColorInt color: Int): BackgroundDrawableBuilder {
        pressedColor = color
        return this
    }

    /**
     * Sets the background color.
     *
     * @param color The background color.
     * @return The builder instance.
     */
    fun setBackgroundColor(@ColorInt color: Int): BackgroundDrawableBuilder {
        backgroundColor = color
        return this
    }

    /**
     * Sets the border radius.
     *
     * @param dps The border radius in DPs.
     * @param borderRadiusFlag Border radius flag.
     * @return The builder instance.
     */
    fun setBorderRadius(
        dps: Float,
        @BorderRadius.BorderRadiusFlag borderRadiusFlag: Int
    ): BackgroundDrawableBuilder {
        this.borderRadiusFlag = borderRadiusFlag
        borderRadiusDps = dps
        return this
    }

    /**
     * Sets the stroke width.
     *
     * @param dps The width in DPs.
     * @return The builder instance.
     */
    fun setStrokeWidth(@Dimension(DP) dps: Int): BackgroundDrawableBuilder {
        strokeWidthDps = dps
        return this
    }

    /**
     * Sets the stroke color. Defaults to the background color.
     *
     * @param strokeColor The stroke color.
     * @return The builder instance.
     */
    fun setStrokeColor(@ColorInt strokeColor: Int): BackgroundDrawableBuilder {
        this.strokeColor = strokeColor
        return this
    }

    /**
     * Builds the drawable.
     *
     * @return The background drawable.
     */
    fun build(): Drawable {
        val strokeWidthPixels = Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                strokeWidthDps.toFloat(),
                context.resources.displayMetrics
            )
        )
        val strokeColor = strokeColor ?: backgroundColor
        val borderRadiusPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            borderRadiusDps,
            context.resources.displayMetrics
        )
        val borderRadii = BorderRadius.createRadiiArray(borderRadiusPixels, borderRadiusFlag)
        val background = GradientDrawable()
        background.shape = GradientDrawable.RECTANGLE
        background.cornerRadii = borderRadii
        background.setColor(backgroundColor)
        background.setStroke(strokeWidthPixels, strokeColor)

        val pressed = pressedColor ?: return background

        val list = ColorStateList.valueOf(pressed)
        val rectShape = RoundRectShape(borderRadii, null, null)
        val mask = ShapeDrawable(rectShape)
        return RippleDrawable(list, background, mask)
    }

    companion object {

        /**
         * Create a new builder.
         *
         * @param context The application context.
         * @return The builder instance.
         */
        fun newBuilder(context: Context): BackgroundDrawableBuilder {
            return BackgroundDrawableBuilder(context)
        }
    }
}
