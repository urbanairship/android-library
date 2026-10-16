/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.R
import android.content.Context
import android.widget.Checkable
import android.widget.ImageView
import androidx.annotation.MainThread
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.shape.Shape

public class ShapeView @JvmOverloads public constructor(
    context: Context,
    private val checkedShapes: List<Shape>,
    private val uncheckedShapes: List<Shape>,
    private val checkedIcon: Image.Icon? = null,
    private val uncheckedIcon: Image.Icon? = null
) : ImageView(context), Checkable, Clippable {

    private val clippableViewDelegate = ClippableViewDelegate()

    private var isChecked = false

    init {
        id = generateViewId()
        scaleType = ScaleType.CENTER_INSIDE

        rebuildDrawable()
    }

    /** Builds its checked and unchecked shapes again, resolving their colors against the context now. */
    internal fun rebuildDrawable() {
        setImageDrawable(
            Shape.Companion.buildStateListDrawable(context, checkedShapes, uncheckedShapes, checkedIcon, uncheckedIcon)
        )
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
