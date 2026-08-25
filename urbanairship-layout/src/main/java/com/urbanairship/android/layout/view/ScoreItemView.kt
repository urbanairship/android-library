package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.Checkable
import androidx.annotation.Dimension
import androidx.appcompat.widget.AppCompatRadioButton
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
    private val padding: Int,
    /**
     * The longest label in the range, which every item in it is sized by.
     *
     * Items sized by their own labels come out different widths — a "1" narrower than a "10" —
     * where the range should read as one row of equal ones. Defaults to this item's own label,
     * for a score of one.
     */
    private val sizingLabel: String = label
) : AppCompatRadioButton(
    context,
    null,
    androidx.appcompat.R.style.Widget_AppCompat_Button_Borderless
), Checkable {

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
        foreground = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple)

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

        LayoutUtils.applyTextAppearance(this, appearance)

        // Once from the paint as it stands, and again after anything the appearance loads in the
        // background — a font family among it — has settled and changed the measurements.
        applySmallestSize()
        post { applySmallestSize() }
    }

    /**
     * Sizes the item to the number it holds, never smaller than something that can be tapped.
     *
     * The number with as much again around it, which is the room iOS gives one: it measures the
     * longest label in the range and adds that measurement again as spacing. Sized to the text
     * alone, an item was the number and nothing else — a row of large digits in circles barely
     * wider than they were.
     *
     * Square, so the shape drawn in it is the shape the layout asked for.
     */
    private fun applySmallestSize() {
        val textWidth = paint.measureText(sizingLabel)
        val textHeight = paint.fontMetrics.let { it.descent - it.ascent }
        val minimumTouchTarget = LayoutUtils.dpToPx(context, MIN_TAPPABLE_DP).toFloat()

        val side = maxOf(minimumTouchTarget, textWidth * 2f, textHeight * 2f).toInt()
        if (side == minimumWidth && side == minimumHeight) return

        minimumHeight = side
        minimumWidth = side
        requestLayout()
    }

    override fun setChecked(checked: Boolean) {
        if (checked != isChecked) {
            super.setChecked(checked)
            refreshDrawableState()
            updateTextState()
        }
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    companion object {
        val CHECKED_STATE_SET: IntArray = intArrayOf(android.R.attr.state_checked)

        /**
         * The smallest a score item is drawn, which is a tappable one.
         *
         * Matches iOS rather than Android's usual 48dp minimum touch target, so score items are
         * the same size on both platforms.
         */
        @Dimension(unit = Dimension.DP)
        private const val MIN_TAPPABLE_DP: Int = 44
    }
}
