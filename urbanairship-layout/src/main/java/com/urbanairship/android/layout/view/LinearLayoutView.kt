/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.animation.LayoutTransition
import android.content.Context
import android.view.Gravity.CENTER_HORIZONTAL
import android.view.Gravity.CENTER_VERTICAL
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LinearLayoutModel
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size.DimensionType.ABSOLUTE
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.dpToPx
import com.urbanairship.android.layout.widget.Clippable
import com.urbanairship.android.layout.widget.ClippableViewDelegate
import com.urbanairship.android.layout.widget.WeightlessLinearLayout
import com.urbanairship.android.layout.widget.WeightlessLinearLayout.LayoutParams.WRAP_CONTENT

internal class LinearLayoutView(
    context: Context,
    model: LinearLayoutModel,
    private val viewEnvironment: ViewEnvironment
) : WeightlessLinearLayout(context), BaseView, Clippable {

    private val clippableViewDelegate: ClippableViewDelegate = ClippableViewDelegate()

    init {
        clipChildren = false
        LayoutUtils.applyBorderAndBackground(this, model)
        orientation = if (model.viewInfo.direction == Direction.VERTICAL) VERTICAL else HORIZONTAL
        gravity = if (model.viewInfo.direction == Direction.VERTICAL) CENTER_HORIZONTAL else CENTER_VERTICAL
        addItems(model.items)

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                isEnabled = enabled
            }
        }

        layoutTransition = LayoutTransition().apply {
            // Prevent unwanted flickering when switching visibility between two views
            disableTransitionType(LayoutTransition.APPEARING)
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, _: WindowInsetsCompat ->
            val noInsets = WindowInsetsCompat.Builder()
                .setInsets(systemBars(), Insets.NONE)
                .build()
            for (i in 0 until childCount) {
                ViewCompat.dispatchApplyWindowInsets(getChildAt(i), noInsets)
            }
            noInsets
        }
    }

    private fun addItems(items: List<LinearLayoutModel.Item>) {
        for (i in items.indices) {
            val (itemInfo, itemModel) = items[i]
            val lp = generateItemLayoutParams(itemInfo)
            val itemView = itemModel.createView(context, viewEnvironment, ItemProperties(itemInfo.size)).apply {
                layoutParams = lp
            }
            // Add view after any existing children, without requesting a layout pass on the child.
            addViewInLayout(itemView, -1, lp, true)
        }
    }

    private fun generateItemLayoutParams(itemInfo: LinearLayoutItemInfo): LayoutParams {
        val size = itemInfo.size
        val w = size.width
        val h = size.height

        val (width, maxWidthPercent) = when (w.type) {
            AUTO -> WRAP_CONTENT to 0f
            ABSOLUTE -> dpToPx(context, w.int).toInt() to 0f
            PERCENT -> 0 to w.float
        }

        val (height, maxHeightPercent) = when (h.type) {
            AUTO -> WRAP_CONTENT to 0f
            ABSOLUTE -> dpToPx(context, h.int).toInt() to 0f
            PERCENT -> 0 to h.float
        }

        val lp = LayoutParams(width, height, maxWidthPercent, maxHeightPercent).apply {
            itemInfo.margin?.let { margin ->
                topMargin = dpToPx(context, margin.top).toInt()
                bottomMargin = dpToPx(context, margin.bottom).toInt()
                marginStart = dpToPx(context, margin.start).toInt()
                marginEnd = dpToPx(context, margin.end).toInt()
            }
        }
        return lp
    }

    override fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }
}
