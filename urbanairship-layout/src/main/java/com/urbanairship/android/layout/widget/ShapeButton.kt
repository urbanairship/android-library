/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.SoundEffectConstants
import android.view.View
import android.widget.Checkable
import androidx.annotation.Dimension
import androidx.annotation.MainThread
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.android.layout.util.LayoutUtils.applyTextAppearance

public open class ShapeButton @JvmOverloads public constructor(
    context: Context,
    checkedShapes: List<Shape>,
    uncheckedShapes: List<Shape>,
    checkedIcon: Image.Icon? = null,
    uncheckedIcon: Image.Icon? = null,
    private val text: String? = null,
    private val checkedTextAppearance: TextAppearance? = null,
    private val uncheckedTextAppearance: TextAppearance? = null
) : AppCompatButton(context), Checkable, Clippable {

    private val clippableViewDelegate: ClippableViewDelegate = ClippableViewDelegate()

    private var isChecked = false

    private var checkedChangeListener: OnCheckedChangeListener? = null

    init {

        val background = Shape.Companion.buildStateListDrawable(
            context, checkedShapes, uncheckedShapes, checkedIcon, uncheckedIcon
        )
        setBackground(background)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            foreground = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple)
        }

        updateText()

        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER
    }

    override fun setChecked(checked: Boolean) {
        if (checked == isChecked) {
            return
        }

        isChecked = checked
        refreshDrawableState()
        updateText()
        checkedChangeListener?.onCheckedChanged(this, checked)
    }

    override fun isChecked(): Boolean {
        return isChecked
    }

    override fun toggle() {
        setChecked(!isChecked)
    }

    public override fun onCreateDrawableState(extraSpace: Int): IntArray? {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    override fun performClick(): Boolean {
        toggle()

        val handled = super.performClick()
        if (!handled) {
            // View only makes a sound effect if the onClickListener was
            // called, so we'll need to make one here instead.
            playSoundEffect(SoundEffectConstants.CLICK)
        }

        return handled
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

    public fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        this.checkedChangeListener = listener
    }

    private fun updateText() {
        if (text == null) { return }
        val checkedAppearance = checkedTextAppearance ?: return
        val uncheckedAppearance = uncheckedTextAppearance ?: return

        val appearance = if (isChecked) checkedAppearance else uncheckedAppearance
        applyTextAppearance(this, appearance)
    }

    public fun interface OnCheckedChangeListener {

        /**
         * Called when the checked state has changed.
         *
         * @param view The button view whose state has changed.
         * @param isChecked  The new checked state of button.
         */
        public fun onCheckedChanged(view: View, isChecked: Boolean)
    }

    public companion object {

        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }
}
