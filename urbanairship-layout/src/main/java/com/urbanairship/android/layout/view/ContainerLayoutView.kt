/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.animation.LayoutTransition
import kotlin.math.max
import kotlin.math.roundToInt
import android.content.Context
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.model.ContainerLayoutModel.Item
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.ClippableConstraintLayout
import com.urbanairship.android.layout.widget.borrowedPercentBase
import com.urbanairship.android.layout.widget.ShrinkableView
import androidx.core.view.OnApplyWindowInsetsListener as OnApplyWindowInsetsListenerCompat

internal class ContainerLayoutView(
    context: Context,
    private val model: ContainerLayoutModel,
    private val viewEnvironment: ViewEnvironment
) : ClippableConstraintLayout(context), BaseView, ShrinkableView {

    private val frameShouldIgnoreSafeArea = SparseBooleanArray()
    private val frameMargins = SparseArray<Margin>()
    // frameId -> aspectRatio for height-auto + percent-width items.
    // ConstraintLayout can't derive ratio height from percent width in AT_MOST parents
    // (both MATCH_CONSTRAINT), so onMeasure does a two-pass fix for these frames.
    private val frameHeightRatios = SparseArray<Double>()
    // frameId -> percent, for items sized as a percent of us on that axis. Only consulted when our
    // own size isn't fixed by our parent; otherwise ConstraintLayout resolves them natively.
    private val framePercentWidths = SparseArray<Float>()
    private val framePercentHeights = SparseArray<Float>()

    init {
        clipChildren = true
        val constraintBuilder = ConstraintSetBuilder.newBuilder(context)
        addItems(model.items, constraintBuilder)
        constraintBuilder.build().applyTo(this)
        ViewCompat.setOnApplyWindowInsetsListener(this, WindowInsetsListener(constraintBuilder))

        layoutTransition = LayoutTransition().apply {
            // Prevent unwanted flickering when switching visibility between two views
            disableTransitionType(LayoutTransition.APPEARING)
        }

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@ContainerLayoutView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@ContainerLayoutView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@ContainerLayoutView, old, new)
            }
        }
    }

    override fun isShrinkable(): Boolean = true

    private fun addItems(items: List<Item>, constraintBuilder: ConstraintSetBuilder) {
        for (item in items) {
            addItem(constraintBuilder, item)
        }
    }

    private fun addItem(constraintBuilder: ConstraintSetBuilder, item: Item) {
        val itemView = item.model.createView(context, viewEnvironment, ItemProperties(item.info.size))

        val frameId = generateViewId()
        val frame: ViewGroup = FrameLayout(context).apply {
            id = frameId
            addView(itemView, MATCH_PARENT, MATCH_PARENT)
        }

        addView(frame)

        val info = item.info
        constraintBuilder
            .position(info.position, frameId)
            .size(info.size, frameId)
            .margin(info.margin, frameId)

        frameShouldIgnoreSafeArea.put(frameId, info.ignoreSafeArea)
        frameMargins.put(frameId, info.margin ?: Margin.NONE)

        // Track items that need the 2-pass height fix (percent width + auto height + ratio).
        val size = info.size
        if (size.aspectRatio != null && size.height.isAuto && size.width.isPercent) {
            frameHeightRatios.put(frameId, size.aspectRatio)
        } else {
            // Ratio items derive their percent width through the fix above; don't resolve twice.
            if (size.width.isPercent) framePercentWidths.put(frameId, size.width.getFloat())
            if (size.height.isPercent) framePercentHeights.put(frameId, size.height.getFloat())
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        // Percent items only need help when our own size isn't fixed by our parent. Given an
        // EXACTLY spec, ConstraintLayout resolves percentages against it natively.
        val autoWidths = widthMode != MeasureSpec.EXACTLY && framePercentWidths.size() > 0
        val autoHeights = heightMode != MeasureSpec.EXACTLY && framePercentHeights.size() > 0

        // Measured unbounded on an axis means we have no size of our own to resolve against, so we
        // look for a scroll layout above us to borrow a viewport from.
        val borrowedWidth =
            if (autoWidths && widthMode == MeasureSpec.UNSPECIFIED) borrowedPercentBase(horizontal = true) else 0
        val borrowedHeight =
            if (autoHeights && heightMode == MeasureSpec.UNSPECIFIED) borrowedPercentBase(horizontal = false) else 0

        // ...and we can only resolve when one of those two panned out. With neither, percent items
        // keep the content size they measure below, matching iOS, where a percent with no parent
        // size to work from falls back to auto rather than collapsing.
        val resolveWidths = autoWidths && (widthMode == MeasureSpec.AT_MOST || borrowedWidth > 0)
        val resolveHeights = autoHeights && (heightMode == MeasureSpec.AT_MOST || borrowedHeight > 0)

        // A percent item may still be carrying an exact pixel size we gave it during a pass measured
        // under a different spec. Hand those axes back to ConstraintLayout, which resolves the
        // percentage against our settled size — otherwise the stale value survives into a pass that
        // returns early below, and the item keeps a size derived from the wrong parent.
        if (widthMode == MeasureSpec.EXACTLY) {
            forEachFrame(framePercentWidths) { it.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT }
        }
        if (heightMode == MeasureSpec.EXACTLY) {
            forEachFrame(framePercentHeights) { it.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT }
        }

        if (frameHeightRatios.size() == 0 && !autoWidths && !autoHeights) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // First pass: measure percent items as auto. They can't resolve against a size we don't
        // have yet, and this clears any size left on them by an earlier pass.
        if (autoWidths) {
            forEachFrame(framePercentWidths) { it.width = ViewGroup.LayoutParams.WRAP_CONTENT }
        }
        if (autoHeights) {
            forEachFrame(framePercentHeights) { it.height = ViewGroup.LayoutParams.WRAP_CONTENT }
        }

        // Ratio items: MATCH_CONSTRAINT width (so constrainPercentWidth applies) + WRAP_CONTENT
        // height (so ratio + MATCH_CONSTRAINT height doesn't corrupt the width measurement).
        for (i in 0 until frameHeightRatios.size()) {
            val frame = findViewById<View>(frameHeightRatios.keyAt(i)) ?: continue
            val lp = frame.layoutParams as ConstraintLayout.LayoutParams
            lp.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        var needsRemeasure = false

        if (resolveWidths) {
            val base = percentBase(widthMode, borrowedWidth, framePercentWidths, horizontal = true)
            needsRemeasure = resolvePercentFrames(framePercentWidths, base, horizontal = true) ||
                    needsRemeasure
        }
        if (resolveHeights) {
            val base = percentBase(heightMode, borrowedHeight, framePercentHeights, horizontal = false)
            needsRemeasure = resolvePercentFrames(framePercentHeights, base, horizontal = false) ||
                    needsRemeasure
        }

        // Compute correct ratio dimensions. For EXACTLY parents apply .fit — if naturalHeight
        // exceeds the container height, reduce width proportionally to maintain ratio within bounds.
        val containerH = MeasureSpec.getSize(heightMeasureSpec)
        for (i in 0 until frameHeightRatios.size()) {
            val frameId = frameHeightRatios.keyAt(i)
            val ratio = frameHeightRatios.valueAt(i)
            val frame = findViewById<View>(frameId) ?: continue
            val lp = frame.layoutParams as ConstraintLayout.LayoutParams

            val naturalW = frame.measuredWidth
            val naturalH = (naturalW / ratio).roundToInt()

            val (correctW, correctH) = if (heightMode == MeasureSpec.EXACTLY && naturalH > containerH) {
                (containerH * ratio).roundToInt() to containerH
            } else {
                naturalW to naturalH
            }

            if (lp.width != correctW || lp.height != correctH) {
                lp.width = correctW
                lp.height = correctH
                needsRemeasure = true
            }
        }

        if (needsRemeasure) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * The size percent items resolve against on one axis: the viewport borrowed from a scroll
     * layout when we were measured unbounded, or otherwise the box we wrap to.
     *
     * That box is the extent of our *non-percent* items only. A container sized to its content
     * can't take its size from an item that is a fraction of it — `H = max(H * percent)` has no
     * solution but zero — so an auto container holding nothing but percent items measures 0 on
     * that axis, and its items with it. This is where iOS's remeasure loop converges.
     */
    private fun percentBase(
        mode: Int,
        borrowed: Int,
        percents: SparseArray<Float>,
        horizontal: Boolean
    ): Int {
        if (mode == MeasureSpec.UNSPECIFIED) return borrowed

        var base = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE || percents.indexOfKey(child.id) >= 0) continue
            val lp = child.layoutParams as ConstraintLayout.LayoutParams
            base = max(
                base,
                if (horizontal) {
                    child.measuredWidth + lp.leftMargin + lp.rightMargin
                } else {
                    child.measuredHeight + lp.topMargin + lp.bottomMargin
                }
            )
        }
        return base
    }

    /**
     * Applies [base] to the percent items on one axis, clamping into what's left of the base after
     * each item's own margins — matching iOS, which bounds percent sizes the same way.
     *
     * Returns true if any item's layout params changed.
     */
    private fun resolvePercentFrames(
        percents: SparseArray<Float>,
        base: Int,
        horizontal: Boolean
    ): Boolean {
        var changed = false
        for (i in 0 until percents.size()) {
            val frame = findViewById<View>(percents.keyAt(i)) ?: continue
            val lp = frame.layoutParams as ConstraintLayout.LayoutParams
            val margins = if (horizontal) {
                lp.leftMargin + lp.rightMargin
            } else {
                lp.topMargin + lp.bottomMargin
            }
            val resolved = (percents.valueAt(i) * base).toInt()
                .coerceIn(0, (base - margins).coerceAtLeast(0))

            if (horizontal && lp.width != resolved) {
                lp.width = resolved
                changed = true
            } else if (!horizontal && lp.height != resolved) {
                lp.height = resolved
                changed = true
            }
        }
        return changed
    }

    private inline fun forEachFrame(
        frames: SparseArray<Float>,
        block: (ConstraintLayout.LayoutParams) -> Unit
    ) {
        for (i in 0 until frames.size()) {
            val frame = findViewById<View>(frames.keyAt(i)) ?: continue
            block(frame.layoutParams as ConstraintLayout.LayoutParams)
        }
    }

    private inner class WindowInsetsListener(
        private val constraintBuilder: ConstraintSetBuilder
    ) : OnApplyWindowInsetsListenerCompat {
        override fun onApplyWindowInsets(
            v: View,
            windowInsets: WindowInsetsCompat
        ): WindowInsetsCompat {
            val applied = ViewCompat.onApplyWindowInsets(v, windowInsets)
            val insets = applied.getInsets(WindowInsetsCompat.Type.systemBars())
            if (applied.isConsumed || insets == Insets.NONE) {
                return WindowInsetsCompat.CONSUMED
            }

            var constraintsChanged = false
            for (i in 0 until childCount) {
                val child = getChildAt(i) as ViewGroup
                val shouldIgnoreSafeArea = frameShouldIgnoreSafeArea[child.id, false]
                if (shouldIgnoreSafeArea) {
                    ViewCompat.dispatchApplyWindowInsets(child, applied)
                } else {
                    ViewCompat.dispatchApplyWindowInsets(child, applied.inset(insets))
                    // Handle insets by adding onto the child frame's margins.
                    val margin = frameMargins[child.id]
                    constraintBuilder.margin(margin, insets, child.id)
                    constraintsChanged = true
                }
            }
            if (constraintsChanged) {
                constraintBuilder.build().applyTo(this@ContainerLayoutView)
            }
            return applied.inset(insets)
        }
    }
}
