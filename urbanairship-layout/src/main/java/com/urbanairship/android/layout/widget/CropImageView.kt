package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageView
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.VerticalPosition

internal class CropImageView(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var offsetHorizontal = DEFAULT_OFFSET
    private var offsetVertical = DEFAULT_OFFSET

    private var parentWidthSpec: Int = 0
    private var parentHeightSpec: Int = 0

    init {
        setScaleType(ScaleType.MATRIX)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCropOffset()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        applyCropOffset()
    }

    fun setParentLayoutParams(layoutParams: ViewGroup.LayoutParams?) {
        parentWidthSpec = layoutParams?.width ?: 0
        parentHeightSpec = layoutParams?.height ?: 0
        applyCropOffset()
    }

    fun setImagePosition(position: Position?) {
        setScaleType(ScaleType.MATRIX)

        offsetHorizontal = when (position?.horizontal) {
            HorizontalPosition.START -> 0f
            HorizontalPosition.CENTER -> 0.5f
            HorizontalPosition.END -> 1f
            null -> DEFAULT_OFFSET
        }
        offsetVertical = when (position?.vertical) {
            VerticalPosition.TOP -> 0f
            VerticalPosition.CENTER -> 0.5f
            VerticalPosition.BOTTOM -> 1f
            null -> DEFAULT_OFFSET
        }
        applyCropOffset()
    }

    private fun applyCropOffset() {
        val drawable = getDrawable() ?: return
        // Bail if we're not using a matrix scale type.
        if (scaleType != ScaleType.MATRIX) return

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val viewWidth: Int = width - paddingLeft - paddingRight
        val viewHeight: Int = height - paddingTop - paddingBottom

        val scale = when {
            // 100% width, auto height -> scale to fit width
            parentWidthSpec == MATCH_PARENT && parentHeightSpec == WRAP_CONTENT ->
                viewWidth.toFloat() / drawableWidth.toFloat()

            // auto width, 100% height -> scale to fit height
            parentHeightSpec == MATCH_PARENT && parentWidthSpec == WRAP_CONTENT ->
                viewHeight.toFloat() / drawableHeight.toFloat()

            // Drawable is shorter than view. Scale it to fill the view height.
            drawableWidth * viewHeight > drawableHeight * viewWidth ->
                viewHeight.toFloat() / drawableHeight.toFloat()

            // Drawable is taller than view. Scale it to fill the view width.
            else -> viewWidth.toFloat() / drawableWidth.toFloat()
        }

        val widthRatio = viewWidth / scale
        val heightRatio = viewHeight / scale
        val xOffset = offsetHorizontal * (drawableWidth - widthRatio)
        val yOffset = offsetVertical * (drawableHeight - heightRatio)

        val src = RectF(xOffset, yOffset, xOffset + widthRatio, yOffset + heightRatio)
        val dest = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())

        val matrix: Matrix = getImageMatrix().apply {
            // Fit the cropped image into the view bounds.
            setRectToRect(src, dest, Matrix.ScaleToFit.FILL)
        }

        setImageMatrix(matrix)
    }

    companion object {
        private const val DEFAULT_OFFSET = 0.5f
    }
}
