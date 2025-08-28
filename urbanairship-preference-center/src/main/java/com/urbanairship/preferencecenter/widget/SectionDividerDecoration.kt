package com.urbanairship.preferencecenter.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.ui.item.AlertItem
import com.urbanairship.preferencecenter.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.ui.item.SectionItem

internal class SectionDividerDecoration(
    context: Context,
    private val isAnimating: () -> Boolean
) : RecyclerView.ItemDecoration() {
    private val drawable = run {
        val dividerAttr = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.dividerHorizontal, dividerAttr, true)
        ContextCompat.getDrawable(context, dividerAttr.resourceId)
            ?: throw Resources.NotFoundException("Failed to resolve attr 'dividerHorizontal' from theme!")
    }

    private val unlabeledSectionPadding = context.resources.getDimensionPixelSize(com.urbanairship.preferencecenter.core.R.dimen.ua_preference_center_unlabeled_section_item_top_padding)

    private val dividerHeight: Int = drawable.intrinsicHeight

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (isAnimating()) return

        if (shouldDrawDividerBelow(view, parent)) {
            outRect.bottom = dividerHeight
        } else if (isSectionWithoutLabeledBreak(view, parent)) {
            outRect.top = unlabeledSectionPadding
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (isAnimating()) return

        val width = parent.width
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (shouldDrawDividerBelow(child, parent)) {
                val top = (child.y + child.height).toInt()
                drawable.setBounds(0, top, width, top + dividerHeight)
                drawable.draw(c)
            }
        }
    }

    private fun shouldDrawDividerBelow(view: View, parent: RecyclerView): Boolean {
        val holder = parent.getChildViewHolder(view)
        val isNotSectionItem = holder !is SectionItem.ViewHolder &&
                holder !is SectionBreakItem.ViewHolder

        val index = parent.indexOfChild(view)
        return if (index < parent.childCount - 1) {
            val nextView = parent.getChildAt(index + 1)
            val nextHolder = parent.getChildViewHolder(nextView)
            val isNextSectionItem = nextHolder is SectionItem.ViewHolder ||
                    nextHolder is SectionBreakItem.ViewHolder
            val isNextAlert = nextHolder is AlertItem.ViewHolder

            isNotSectionItem && isNextSectionItem || isNextAlert
        } else {
            false
        }
    }

    private fun isSectionWithoutLabeledBreak(view: View, parent: RecyclerView): Boolean {
        val holder = parent.getChildViewHolder(view)
        val isSectionItem = holder is SectionItem.ViewHolder

        val index = parent.indexOfChild(view)
        return if (index < parent.childCount && index > 0) {
            val prevView = parent.getChildAt(index - 1)
            val prevHolder = parent.getChildViewHolder(prevView)
            val isPrevSectionBreak = prevHolder is SectionBreakItem.ViewHolder

            isSectionItem && !isPrevSectionBreak
        } else {
            false
        }
    }
}
