/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

public class WrappingViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ViewGroup(context, attrs, defStyle) {

    public var itemSpacing: Int = 0
    public var lineSpacing: Int = 0
    public var maxItemsPerLine: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        var totalHeight = 0
        var lineHeight = 0
        var lineWidth = 0
        var lineChildCount = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            child.measure(childWidthSpec, childHeightSpec)

            if (lineChildCount < maxItemsPerLine &&
                lineWidth + child.measuredWidth + (if (lineChildCount > 0) itemSpacing else 0) <= parentWidth
            ) {
                lineWidth += child.measuredWidth + (if (lineChildCount > 0) itemSpacing else 0)
                lineHeight = maxOf(lineHeight, child.measuredHeight)
                lineChildCount++
            } else {
                totalHeight += lineHeight + lineSpacing
                lineWidth = child.measuredWidth
                lineHeight = child.measuredHeight
                lineChildCount = 1
            }
        }

        // Add the last line's height, but no additional line spacing
        totalHeight += lineHeight

        // Figure out the final height
        setMeasuredDimension(parentWidth, resolveSize(totalHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val parentWidth = measuredWidth
        var lineHeight = 0
        var lineWidth = 0
        var y = 0
        val lineItems = mutableListOf<View>()

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (lineWidth + childWidth + (if (lineItems.isNotEmpty()) itemSpacing else 0) > parentWidth) {
                // Center the line and layout
                layoutLine(lineItems, parentWidth, y)
                y += lineHeight + lineSpacing
                lineHeight = 0
                lineWidth = 0
                lineItems.clear()
            }

            lineItems.add(child)
            lineWidth += childWidth + if (lineItems.size > 1) itemSpacing else 0
            lineHeight = maxOf(lineHeight, childHeight)
        }

        // Layout the last line
        if (lineItems.isNotEmpty()) {
            layoutLine(lineItems, parentWidth, y)
        }
    }

    private fun layoutLine(
        lineItems: List<View>,
        parentWidth: Int,
        startY: Int
    ) {
        val totalSpacing = itemSpacing * (lineItems.size - 1)
        val totalLineWidth = lineItems.sumOf { it.measuredWidth } + totalSpacing
        var x = (parentWidth - totalLineWidth) / 2 // Center align the line horizontally

        for (item in lineItems) {
            val left = x
            val top = startY
            val right = left + item.measuredWidth
            val bottom = top + item.measuredHeight

            item.layout(left, top, right, bottom)
            x += item.measuredWidth + itemSpacing
        }
    }
}
