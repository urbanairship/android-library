/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.animation.LayoutTransition
import android.content.Context
import android.view.Gravity.CENTER_HORIZONTAL
import android.view.Gravity.CENTER_VERTICAL
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import androidx.core.graphics.Insets
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.model.Background
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
import com.urbanairship.android.layout.widget.ShrinkableView
import com.urbanairship.android.layout.widget.WeightlessLinearLayout

internal class LinearLayoutView(
    context: Context,
    private val model: LinearLayoutModel,
    private val viewEnvironment: ViewEnvironment
) : WeightlessLinearLayout(context), BaseView, Clippable, ShrinkableView {

    private val clippableViewDelegate: ClippableViewDelegate = ClippableViewDelegate()

    private val isListView: Boolean = model.viewInfo.accessibilityRole?.type == LinearLayoutInfo.AccessibilityRole.Type.LIST_VIEW


    init {
        clipChildren = false
        orientation = if (model.viewInfo.direction == Direction.VERTICAL) OrientationMode.VERTICAL else OrientationMode.HORIZONTAL
        gravity = if (model.viewInfo.direction == Direction.VERTICAL) CENTER_HORIZONTAL else CENTER_VERTICAL
        addItems(model.items)

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                isEnabled = enabled
            }
            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@LinearLayoutView, old, new)
            }
        }

        if (isListView) {
            val listItemCount = model.viewInfo.items.count { it.isListItem }
            val hierarchical = model.viewInfo.accessibilityRole?.hierarchical

            ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View, info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = ListView::class.java.name

                    // Define the collection characteristics
                    val collectionInfo = AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                        listItemCount,
                        1,
                        hierarchical ?: false)
                    info.setCollectionInfo(collectionInfo)

                    // Important: Make sure the view can be focused by the accessibility service
                    info.isScreenReaderFocusable = true
                    info.isFocusable = true
                }
            })
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


    override fun isShrinkable(): Boolean = model.isShrinkable

    private fun addItems(items: List<LinearLayoutModel.Item>) {
        val listItems = if (isListView) {
            items.filter { it.info.isListItem }.map { it.info }
        } else {
            items
        }

        for (i in items.indices) {
            val (itemInfo, itemModel) = items[i]
            val lp = generateItemLayoutParams(itemInfo)
            val itemView = itemModel.createView(context, viewEnvironment, ItemProperties(itemInfo.size))

            if (isListView && itemInfo.isListItem) {
                val listIndex = listItems.indexOf(itemInfo)
                val accessibilityWrapper = FrameLayout(context).apply {
                    layoutParams = lp
                }

                ViewCompat.setAccessibilityDelegate(accessibilityWrapper, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)

                        val collectionItemInfo = AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                            listIndex,     // rowIndex
                            1,     // rowSpan
                            0,     // columnIndex
                            1,     // columnSpan
                            false  // heading
                        )
                        info.setCollectionItemInfo(collectionItemInfo)

                        info.isScreenReaderFocusable = true

                    }
                })

                accessibilityWrapper.addView(itemView)
                addViewInLayout(accessibilityWrapper, -1, lp, true)
            } else {
                itemView.layoutParams = lp
                addViewInLayout(itemView, -1, lp, true)
            }
        }
    }

    private fun generateItemLayoutParams(itemInfo: LinearLayoutItemInfo): LayoutParams {
        val size = itemInfo.size
        val w = size.width
        val h = size.height

        val (width, maxWidthPercent) = when (w.type) {
            AUTO -> MarginLayoutParams.WRAP_CONTENT to 0f
            ABSOLUTE -> dpToPx(context, w.getInt()).toInt() to 0f
            PERCENT -> 0 to w.getFloat()
        }

        val (height, maxHeightPercent) = when (h.type) {
            AUTO -> MarginLayoutParams.WRAP_CONTENT to 0f
            ABSOLUTE -> dpToPx(context, h.getInt()).toInt() to 0f
            PERCENT -> 0 to h.getFloat()
        }

        val lp = LayoutParams(width, height, maxWidthPercent, maxHeightPercent).apply {
            itemInfo.margin?.let { margin ->
                topMargin = dpToPx(context, margin.top).toInt()
                bottomMargin = dpToPx(context, margin.bottom).toInt()
                marginStart = dpToPx(context, margin.start).toInt()
                marginEnd = dpToPx(context, margin.end).toInt()
            }

            itemInfo.position?.let {
                gravity = it.getGravity()
            }
        }
        return lp
    }

    override fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }

    override fun setClipPathBorderRadius(borderRadii: FloatArray?) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii)
    }
}

private val LinearLayoutItemInfo.isListItem: Boolean
    get() { return accessibilityRole?.type == LinearLayoutItemInfo.AccessibilityRole.Type.LIST_ITEM }
