/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Checkable
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.PagerIndicatorModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.widget.ShapeView
import com.urbanairship.android.layout.widget.ShrinkableView
import kotlin.math.min
import com.urbanairship.R as CoreR

internal class PagerIndicatorView(
    context: Context,
    private val model: PagerIndicatorModel
) : LinearLayout(context), BaseView, ShrinkableView {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        isFocusable = false
        isFocusableInTouchMode = false
        if (model.announcePage) {
            // The view must be important for accessibility so that the page
            // number is announced via live region updates on this view.
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        } else {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        model.listener = object : PagerIndicatorModel.Listener {
            private var itemsCount = 0

            override fun onUpdate(size: Int, position: Int) {
                if (size != itemsCount) {
                    setCount(size)
                    itemsCount = size
                }
                setPosition(position)
            }

            override fun setVisibility(visible: Boolean) {
                this@PagerIndicatorView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@PagerIndicatorView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@PagerIndicatorView, old, new)
            }
        }
    }

    /** Dots give where a stated length can't, so a row short of width narrows them rather than
     * dropping them. */
    override fun isShrinkable(): Boolean = true

    /**
     * Lays the dots out square, at the height the item stated or at [DEFAULT_DOT_SIZE_DP], and no
     * wider between them than the width on offer.
     *
     * A shape draws into the bounds it is given and reports no size of its own, so dots take
     * theirs from the row. An item that states `auto` leaves the row taking its height from the
     * dots in turn, and the pair of them settle at nothing: `100% x auto` drew six 1px dots.
     *
     * The width they add up to is a demand the row has to meet out of something. Asking for more
     * than it has leaves it with nothing to take it from, and a row that can't fit its content
     * drops what isn't a stated length — which is the dots.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            ResourceUtils.dpToPx(context, DEFAULT_DOT_SIZE_DP).toInt()
        }

        if (childCount > 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val spacing = (0 until childCount).sumOf {
                val lp = getChildAt(it).layoutParams as MarginLayoutParams
                lp.marginStart + lp.marginEnd
            }
            val room = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd - spacing
            size = min(size, (room / childCount).coerceAtLeast(0))
        }

        for (i in 0 until childCount) {
            getChildAt(i).layoutParams.width = size
        }

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }

    /**
     * Sets the number of indicator dots to be displayed.
     *
     * @param count The number of dots to display.
     */
    fun setCount(count: Int) {
        removeAllViews()

        val bindings = model.viewInfo.bindings
        val checked = bindings.selected
        val unchecked = bindings.unselected
        val spacing = ResourceUtils.dpToPx(context, model.viewInfo.indicatorSpacing).toInt()
        val halfSpacing = (spacing / 2f).toInt()
        for (i in 0 until count) {
            val view: ImageView =
                ShapeView(context, checked.shapes, unchecked.shapes, checked.icon, unchecked.icon)
                    .apply {
                        id = model.getIndicatorViewId(i)
                        adjustViewBounds = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    }

            val lp = LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply {
                marginStart = if (i == 0) spacing else halfSpacing
                marginEnd = if (i == count - 1) spacing else halfSpacing
            }
            addView(view, lp)
        }
    }

    private companion object {

        /** What a dot is when nothing states a height, matching iOS. */
        private const val DEFAULT_DOT_SIZE_DP = 32
    }

    /**
     * Updates the highlighted dot view in the indicator.
     *
     * @param position The position of the dot to highlight.
     */
    fun setPosition(position: Int) {
        for (i in 0 until childCount) {
            (getChildAt(i) as Checkable).isChecked = i == position
        }
        if (model.announcePage) {
            contentDescription = context.getString(CoreR.string.ua_pager_progress, position + 1, childCount)
        }
    }
}
