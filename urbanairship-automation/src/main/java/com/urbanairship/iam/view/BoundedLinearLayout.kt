/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi

/** LinearLayout that supports max width. */
internal open class BoundedLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
) : LinearLayout(context, attrs, defStyle, defResStyle) {

    private val boundedViewDelegate = BoundedViewDelegate(context, attrs, defStyle, defResStyle)
    private val clippableViewDelegate = ClippableViewDelegate()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            boundedViewDelegate.getWidthMeasureSpec(widthMeasureSpec),
            boundedViewDelegate.getHeightMeasureSpec(heightMeasureSpec)
        )
    }

    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }
}
