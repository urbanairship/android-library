/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Canvas
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
import com.urbanairship.android.layout.property.Background as BackgroundLayer
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size.DimensionType.ABSOLUTE
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.dpToPx
import com.urbanairship.android.layout.widget.BackgroundsPainter
import com.urbanairship.android.layout.widget.VideoBackgroundChildren
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

    // Never touched by measure/layout above -- see `BackgroundsPainter`.
    private val backgroundsPainter = BackgroundsPainter(context, viewEnvironment) {
        // postInvalidate(), not invalidate() -- the image loader's onImageLoaded callback
        // is not guaranteed to run on the UI thread, and invalidate() silently no-ops when
        // called off it.
        postInvalidate()
    }

    private val videoBackgrounds = VideoBackgroundChildren(
        layers = model.viewInfo.backgrounds.orEmpty().map { it to null },
        models = model.videoBackgroundModels,
        context = context,
        viewEnvironment = viewEnvironment
    )

    init {
        backgroundsPainter.backgrounds = model.viewInfo.backgrounds
        backgroundsPainter.attachedViewBackgrounds = videoBackgrounds.paintedByView
        videoBackgrounds.addTo(this)
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

    override fun isExcludedFromStack(child: View): Boolean = videoBackgrounds.contains(child)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Left out of the stack above, so it never got measured or placed there.
        videoBackgrounds.placeInto(backgroundsPainter, width, height)
    }

    override fun dispatchDraw(canvas: Canvas) {
        backgroundsPainter.drawLayersBefore(canvas, width, height)
        super.dispatchDraw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val result = super.drawChild(canvas, child, drawingTime)
        // Anything stacked above a layer drawn as a real view has to blend against that view's
        // live pixels, so it goes down here rather than before the children.
        val attachedIndex = videoBackgrounds.indexOf(child)
        if (attachedIndex >= 0) {
            backgroundsPainter.drawLayersAfter(canvas, width, height, attachedIndex)
        }
        return result
    }

    private fun addItems(items: List<LinearLayoutModel.Item>) {
        val listItems = if (isListView) {
            items.filter { it.info.isListItem }.map { it.info }
        } else {
            items
        }

        for (i in items.indices) {
            val (itemInfo, itemModel) = items[i]
            val lp = generateItemLayoutParams(itemInfo)
            val itemView = itemModel.createView(context, viewEnvironment, ItemProperties(itemInfo.size, itemInfo.position))

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

        // Whether anything under this item supplies a length is the measuring pass's question, not
        // ours. `WeightlessLinearLayout` answers it by handing the percentages no base, which leaves
        // them to size to their own content — the same answer iOS and web give, the latter for free
        // because a CSS percentage against an auto-height box resolves to `auto`. Zeroing the item
        // here pre-empted that with the opposite answer, and did it first, so the item never got to
        // measure at all.
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

        val lp = LayoutParams(width, height, maxWidthPercent, maxHeightPercent, size.aspectRatio?.toFloat() ?: 0f).apply {
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
