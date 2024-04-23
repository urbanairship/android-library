/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.urbanairship.R

/**
 * View delegate to support bounded views.
 */
internal class BoundedViewDelegate(
    context: Context,
    attributeSet: AttributeSet?,
    defStyle: Int,
    defResStyle: Int
) {

    private var maxWidth = 0
    private var maxHeight = 0

    init {
        if (attributeSet != null) {
            val attributes = context.theme.obtainStyledAttributes(
                attributeSet,
                R.styleable.UrbanAirshipLayout,
                defStyle,
                defResStyle
            )
            maxWidth = attributes.getDimensionPixelSize(
                R.styleable.UrbanAirshipLayout_urbanAirshipMaxWidth,
                0
            )
            maxHeight = attributes.getDimensionPixelSize(
                R.styleable.UrbanAirshipLayout_urbanAirshipMaxHeight,
                0
            )
            attributes.recycle()
        }
    }

    /**
     * Gets the measured width spec.
     *
     * @param widthMeasureSpec The view's measure width spec.
     * @return The measured width spec.
     */
    fun getWidthMeasureSpec(widthMeasureSpec: Int): Int {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        return if (maxWidth in 1..<width) {
            View.MeasureSpec.makeMeasureSpec(
                maxWidth,
                View.MeasureSpec.getMode(widthMeasureSpec)
            )
        } else {
            widthMeasureSpec
        }
    }

    /**
     * Gets the measured height spec.
     *
     * @param heightMeasureSpec The view's measure height spec.
     * @return The measured width spec.
     */
    fun getHeightMeasureSpec(heightMeasureSpec: Int): Int {
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        return if (maxHeight in 1..<height) {
            View.MeasureSpec.makeMeasureSpec(
                maxHeight, View.MeasureSpec.getMode(heightMeasureSpec)
            )
        } else {
            heightMeasureSpec
        }
    }
}
