package com.urbanairship.android.layout.widget

import android.graphics.Matrix
import android.graphics.RectF
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import java.lang.ref.WeakReference
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.view.ImageButtonView
import com.urbanairship.android.layout.view.MediaView

/**
 * Delegate for shared `ImageView` cropping logic. Used by `CropImageButton` and `CropImageView`,
 * which are base classes that are used in [ImageButtonView] and [MediaView].
 */
internal class CropImageDelegate(view: ImageView) {

    private val weakView = WeakReference(view)

    private var offsetHorizontal = DEFAULT_OFFSET
    private var offsetVertical = DEFAULT_OFFSET

    private var parentWidthSpec: Int = 0
    private var parentHeightSpec: Int = 0

    init {
        view.scaleType = ScaleType.MATRIX
    }

    fun onSizeChanged() {
        applyCropOffset()
    }

    fun setImageDrawable() {
        applyCropOffset()
    }

    fun setParentLayoutParams(layoutParams: ViewGroup.LayoutParams?) {
        parentWidthSpec = layoutParams?.width ?: 0
        parentHeightSpec = layoutParams?.height ?: 0
        applyCropOffset()
    }

    fun setImagePosition(position: Position?) {
        val view = weakView.get() ?: return

        view.setScaleType(ScaleType.MATRIX)

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
        val view = weakView.get() ?: return
        val drawable = view.getDrawable() ?: return

        // Bail if we're not using a matrix scale type.
        if (view.scaleType != ScaleType.MATRIX) return

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val viewWidth: Int = with(view) { width - paddingLeft - paddingRight }
        val viewHeight: Int = with(view) { height - paddingTop - paddingBottom }

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

        val matrix: Matrix = view.getImageMatrix().apply {
            // Fit the cropped image into the view bounds.
            setRectToRect(src, dest, Matrix.ScaleToFit.FILL)
        }

        view.setImageMatrix(matrix)
    }

    companion object {
        private const val DEFAULT_OFFSET = 0.5f
    }
}
