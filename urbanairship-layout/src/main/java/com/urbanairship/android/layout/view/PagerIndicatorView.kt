/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Checkable
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.PagerIndicatorModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.widget.ShapeView

internal class PagerIndicatorView(
    context: Context,
    private val model: PagerIndicatorModel
) : LinearLayout(context), BaseView {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        if (model.announcePage) {
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
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

    /**
     * Updates the highlighted dot view in the indicator.
     *
     * @param position The position of the dot to highlight.
     */
    fun setPosition(position: Int) {
        for (i in 0 until childCount) {
            (getChildAt(i) as Checkable).isChecked = i == position
        }
        if (model.announcePage == true) {
            val announcement =
                context.getString(com.urbanairship.R.string.ua_pager_progress, position + 1, childCount)
            this.contentDescription = announcement
            this.announceForAccessibility(announcement)
        }
    }
}
