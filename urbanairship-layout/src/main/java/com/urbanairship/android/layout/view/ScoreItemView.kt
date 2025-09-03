package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Checkable
import androidx.annotation.Dimension
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.android.layout.util.LayoutUtils

internal class ScoreItemView(
    private val context: Context,
    private val label: String,
    private val bindings: ScoreStyle.Bindings,
    @Dimension(unit = Dimension.DP)
    private val padding: Int
) : AppCompatButton(
    context,
    null,
    androidx.appcompat.R.style.Widget_AppCompat_Button_Borderless
), Checkable {

    private var isChecked = false

    init {
        id = generateViewId()

        isAllCaps = false
        isSingleLine = true
        includeFontPadding = true

        val background: Drawable = Shape.buildStateListDrawable(
            context,
            bindings.selected.shapes,
            bindings.unselected.shapes,
            null,
            null
        )

        setBackground(background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            foreground = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple)
        }

        configure()
    }

    private fun configure() {
        text = label

        updateTextState()
        updatePadding(padding, padding, padding, padding)
    }

    private fun updateTextState() {
        val selectedTextAppearance = bindings.selected.textAppearance
        val unselectedTextAppearance = bindings.unselected.textAppearance
        val appearance = if (isChecked) selectedTextAppearance else unselectedTextAppearance

        post {
            // Just use the raw font height without padding
            val textHeight = paint.fontMetrics.let { it.descent - it.ascent }.toInt()

            // Convert minimum touch target to pixels
            val minimumTouchTarget = LayoutUtils.dpToPx(context, 44)

            // Use the larger of the text height or minimum touch target
            minimumHeight = maxOf(minimumTouchTarget, textHeight)
            minimumWidth = minimumHeight

            requestLayout()
        }

        LayoutUtils.applyTextAppearance(this, appearance)
    }

    override fun setChecked(checked: Boolean) {
        if (checked != isChecked) {
            isChecked = checked
            refreshDrawableState()

            updateTextState()
        }
    }

    override fun isChecked(): Boolean {
        return isChecked
    }

    override fun toggle() {
        setChecked(!isChecked)
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    companion object {
        val CHECKED_STATE_SET: IntArray = intArrayOf(android.R.attr.state_checked)
    }
}
