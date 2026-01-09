/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.shape.Shape
import kotlin.math.min

public class ShapeDrawableWrapper private constructor(
    private val state: ShapeState,
    res: Resources?
) : DrawableWrapper(state, res) {

    private val tempRect = Rect()

    private var gravityPosition: HorizontalPosition = HorizontalPosition.CENTER

    public constructor(context: Context, shape: Shape) : this(
        shape.getDrawable(context), shape.aspectRatio, shape.scale, null
    )

    public constructor(
        drawable: Drawable, aspectRatio: Float, scale: Float, gravityPosition: HorizontalPosition?
    ) : this(ShapeState(null), null) {
        this.gravityPosition = gravityPosition ?: HorizontalPosition.CENTER

        state.aspectRatio = aspectRatio
        state.scale = scale

        setDrawable(drawable)
    }

    override fun onBoundsChange(bounds: Rect) {
        val local = tempRect
        local.set(bounds)

        var width: Int
        var height: Int
        if (state.aspectRatio == 1f) {
            val minDimension = min(bounds.width(), bounds.height())
            width = minDimension
            height = minDimension
        } else if (state.aspectRatio > 1) {
            width = bounds.width()
            height = (bounds.height() / state.aspectRatio).toInt()
        } else {
            width = (bounds.width() * state.aspectRatio).toInt()
            height = bounds.height()
        }

        width = (width * state.scale).toInt()
        height = (height * state.scale).toInt()

        val widthDiff = (bounds.width() - width) / 2
        val heightDiff = (bounds.height() - height) / 2

        when (gravityPosition) {
            HorizontalPosition.CENTER -> {
                local.left += widthDiff
                local.right -= widthDiff
            }

            HorizontalPosition.START -> local.right -= widthDiff * 2
            HorizontalPosition.END -> local.left += widthDiff * 2
        }

        local.top += heightDiff
        local.bottom -= heightDiff

        super.onBoundsChange(local)
    }

    public override fun getIntrinsicWidth(): Int {
        // Override intrinsic width so icons can be rendered full size within our bounds.
        return -1
    }

    public override fun getIntrinsicHeight(): Int {
        // Override intrinsic height so icons can be rendered full size within our bounds.
        return -1
    }

    override fun getConstantState(): ConstantState? {
        state.isChangingConfigurations = state.isChangingConfigurations or changingConfigurations
        return state
    }

    internal class ShapeState (orig: ShapeState?) : DrawableWrapperState(orig) {

        var isChangingConfigurations: Int = 0
        var cachedDrawable: Drawable? = null
        var scale: Float = 0f
        var aspectRatio: Float = 0f

        init {
            if (orig != null) {
                isChangingConfigurations = orig.isChangingConfigurations
                drawableState = orig.drawableState
                cachedDrawable = orig.cachedDrawable
                scale = orig.scale
                aspectRatio = orig.aspectRatio
            }
        }

        override fun newDrawable(res: Resources?): Drawable {
            return ShapeDrawableWrapper(this, res)
        }
    }

    init {
        updateLocalState()
    }

    private fun updateLocalState() {
        state.cachedDrawable?.let(::setDrawable)
    }
}
