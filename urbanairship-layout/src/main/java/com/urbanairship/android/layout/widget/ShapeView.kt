/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.R
import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.Checkable
import android.widget.ImageView
import androidx.annotation.MainThread
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.shape.Shape

public class ShapeView @JvmOverloads public constructor(
    context: Context,
    checkedShapes: List<Shape>,
    uncheckedShapes: List<Shape>,
    checkedIcon: Image.Icon? = null,
    uncheckedIcon: Image.Icon? = null
) : ImageView(context), Checkable, Clippable {

    private val clippableViewDelegate = ClippableViewDelegate()

    private var isChecked = false

    init {
        id = generateViewId()
        scaleType = ScaleType.CENTER_INSIDE

        val drawable: Drawable = Shape.Companion.buildStateListDrawable(
            context, checkedShapes, uncheckedShapes, checkedIcon, uncheckedIcon
        )
        setImageDrawable(drawable)
    }

    override fun setChecked(checked: Boolean) {
        if (checked == isChecked) {
            return
        }

        isChecked = checked
        refreshDrawableState()
    }

    override fun isChecked(): Boolean {
        return isChecked
    }

    override fun toggle() {
        setChecked(!isChecked)
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray? {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    override fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }

    /**
     * {@inheritDoc}
     */
    @MainThread
    override fun setClipPathBorderRadius(borderRadii: FloatArray?) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii)
    }

    public companion object {

        private val CHECKED_STATE_SET = intArrayOf(R.attr.state_checked)
    }
}
