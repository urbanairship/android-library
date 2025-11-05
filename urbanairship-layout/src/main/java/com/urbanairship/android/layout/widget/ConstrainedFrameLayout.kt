/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.Dimension
import androidx.annotation.MainThread
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.widget.ConstrainedViewDelegate.ChildMeasurer

/**
 * FrameLayout that supports min and max dimension constraints.
 *
 * @hide
 */
public class ConstrainedFrameLayout public constructor(
    context: Context,
    size: ConstrainedSize
) : FrameLayout(context), Clippable {

    private val clippableViewDelegate: ClippableViewDelegate = ClippableViewDelegate()
    private val constrainedViewDelegate: ConstrainedViewDelegate = ConstrainedViewDelegate(this, size)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        constrainedViewDelegate.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec,
            { child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int ->
                this.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec)
            }, { widthMeasureSpec: Int, heightMeasureSpec: Int ->
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            })
    }

    /**
     * {@inheritDoc}
     */
    @MainThread
    override fun setClipPathBorderRadius(@Dimension borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }

    /**
     * {@inheritDoc}
     */
    @MainThread
    override fun setClipPathBorderRadius(borderRadii: FloatArray?) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii)
    }
}
