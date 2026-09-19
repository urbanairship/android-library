package com.urbanairship.android.layout.widget

import android.graphics.Matrix
import android.graphics.RectF
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.view.ImageButtonView
import com.urbanairship.android.layout.view.MediaView
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 * Delegate for shared `ImageView` cropping logic. Used by `CropImageButton` and `CropImageView`,
 * which are base classes that are used in [ImageButtonView] and [MediaView].
 */
internal class CropImageDelegate(view: ImageView) {

    private val weakView = WeakReference(view)

    private var offsetHorizontal = DEFAULT_OFFSET
    private var offsetVertical = DEFAULT_OFFSET
    private var fitsWhole = false

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

        offsetHorizontal = position.offsetHorizontal()
        offsetVertical = position.offsetVertical()
        applyCropOffset()
    }

    /**
     * Paints the whole image inside the view, anchored at [position], or hands the view back to
     * [ScaleType.FIT_CENTER].
     *
     * An image fitted whole into a view wider or taller than its own shape leaves slack, and
     * `FIT_CENTER` always splits that evenly. This gives it to the anchor instead.
     *
     * @param position where to anchor the image, or null to centre it.
     */
    fun setWholeImagePosition(position: Position?) {
        val view = weakView.get() ?: return

        fitsWhole = position != null
        if (position == null) {
            view.setScaleType(ScaleType.FIT_CENTER)
            return
        }

        view.setScaleType(ScaleType.MATRIX)
        offsetHorizontal = position.offsetHorizontal()
        offsetVertical = position.offsetVertical()
        applyCropOffset()
    }

    private fun applyCropOffset() {
        val view = weakView.get() ?: return
        val drawable = view.getDrawable() ?: return

        // Bail if we're not using a matrix scale type.
        if (view.scaleType != ScaleType.MATRIX) return

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        if (drawableWidth <= 0 || drawableHeight <= 0) return
        val viewWidth: Int = with(view) { width - paddingLeft - paddingRight }
        val viewHeight: Int = with(view) { height - paddingTop - paddingBottom }

        if (fitsWhole) {
            view.setImageMatrix(
                view.getImageMatrix().apply {
                    setRectToRect(
                        RectF(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat()),
                        wholeImageBounds(drawableWidth, drawableHeight, viewWidth, viewHeight),
                        Matrix.ScaleToFit.FILL
                    )
                }
            )
            return
        }

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

    /**
     * The rectangle the whole image is painted into: as large as it fits in the view without
     * overrunning either axis, with whatever that leaves over given to the anchor.
     */
    private fun wholeImageBounds(
        drawableWidth: Int,
        drawableHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): RectF {
        val scale = min(
            viewWidth.toFloat() / drawableWidth.toFloat(),
            viewHeight.toFloat() / drawableHeight.toFloat()
        )
        val width = drawableWidth * scale
        val height = drawableHeight * scale
        val left = offsetHorizontal * (viewWidth - width)
        val top = offsetVertical * (viewHeight - height)

        return RectF(left, top, left + width, top + height)
    }

    private fun Position?.offsetHorizontal(): Float = when (this?.horizontal) {
        HorizontalPosition.START -> 0f
        HorizontalPosition.CENTER -> 0.5f
        HorizontalPosition.END -> 1f
        null -> DEFAULT_OFFSET
    }

    private fun Position?.offsetVertical(): Float = when (this?.vertical) {
        VerticalPosition.TOP -> 0f
        VerticalPosition.CENTER -> 0.5f
        VerticalPosition.BOTTOM -> 1f
        null -> DEFAULT_OFFSET
    }

    companion object {
        private const val DEFAULT_OFFSET = 0.5f
    }
}
