/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.ConstrainedSize.ConstrainedDimension
import com.urbanairship.android.layout.property.ConstrainedSize.ConstrainedDimensionType
import com.urbanairship.android.layout.util.ResourceUtils.dpToPx
import java.lang.ref.WeakReference
import kotlin.math.max

public class ConstrainedViewDelegate internal constructor(
    view: View,
    private val size: ConstrainedSize
) {

    private val weakView = WeakReference(view)

    public fun interface ChildMeasurer {
        public fun measureChild(child: View, widthMeasureSpec: Int, heightMeasureSpec: Int)
    }

    public fun interface Measurable {
        public fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    }

    public fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        childMeasurer: ChildMeasurer,
        superMeasurer: Measurable
    ) {
        var widthMeasureSpec = widthMeasureSpec
        var heightMeasureSpec = heightMeasureSpec
        val view = weakView.get() ?: return

        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var maxWidth = 0
        var maxHeight = 0

        val wrapContentWidth = view.layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT
        val wrapContentHeight = view.layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT

        if (!wrapContentWidth) {
            maxWidth = widthSize
        }

        if (!wrapContentHeight) {
            maxHeight = heightSize
        }

        if (wrapContentWidth || wrapContentHeight) {
            if (view is ViewGroup) {
                val viewGroup = view
                for (i in 0..<viewGroup.childCount) {
                    val child = viewGroup.getChildAt(i)
                    childMeasurer.measureChild(child, widthMeasureSpec, heightMeasureSpec)
                    val lp = child.layoutParams as MarginLayoutParams
                    if (wrapContentWidth) {
                        maxWidth = max(maxWidth, child.measuredWidth + lp.leftMargin + lp.rightMargin)
                    }
                    if (wrapContentHeight) {
                        maxHeight = max( maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin)
                    }
                }
            }

            val constrainedWidth = constrainDimension(size.minWidth, size.maxWidth, widthSize, maxWidth)
            val constrainedHeight = constrainDimension(size.minHeight, size.maxHeight, heightSize, maxHeight)

            widthMeasureSpec = MeasureSpec.makeMeasureSpec(constrainedWidth, MeasureSpec.EXACTLY)
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(constrainedHeight, MeasureSpec.EXACTLY)
        }

        superMeasurer.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun constrainDimension(
        min: ConstrainedDimension?, max: ConstrainedDimension?, specSize: Int, measuredMaxSize: Int
    ): Int {
        val view = weakView.get() ?: return measuredMaxSize

        var constrainedDimension = measuredMaxSize
        if (min != null) {
            val minSize = when (min.type) {
                ConstrainedDimensionType.PERCENT -> if (specSize > 0) (specSize * min.getFloat()).toInt() else Int.MIN_VALUE
                ConstrainedDimensionType.ABSOLUTE -> dpToPx(view.context, min.getInt()).toInt()
            }

            if (constrainedDimension < minSize) {
                constrainedDimension = minSize
            }
        }
        if (max != null) {
            val maxSize = when (max.type) {
                ConstrainedDimensionType.PERCENT ->  if (specSize > 0) (specSize * max.getFloat()).toInt() else Int.MAX_VALUE
                ConstrainedDimensionType.ABSOLUTE -> dpToPx(view.context, max.getInt()).toInt()
            }

            if (constrainedDimension > maxSize) {
                constrainedDimension = maxSize
            }
        }
        return constrainedDimension
    }
}
