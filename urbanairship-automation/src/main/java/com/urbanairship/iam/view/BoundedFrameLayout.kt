/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi

/**
 * FrameLayout that supports max width.
 *
 * @hide
 */
/**
 * Default constructor.
 *
 * @param context A Context object used to access application assets.
 * @param attrs An AttributeSet passed to our parent.
 * @param defStyle The default style resource ID.
 * @param defResStyle A resource identifier of a style resource that supplies default values for
 * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
 * look for defaults.
 */
internal class BoundedFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
) : FrameLayout(context, attrs, defStyle, defResStyle) {

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
