/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo

/**
 * LinearLayout that supports max width.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
public open class BoundedLinearLayout @JvmOverloads constructor(
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
    public fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }
}
