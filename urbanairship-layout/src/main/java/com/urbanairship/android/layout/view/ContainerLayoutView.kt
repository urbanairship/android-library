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
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.widget.AutoSizeProvider
import com.urbanairship.android.layout.widget.ClippableConstraintLayout
import com.urbanairship.android.layout.widget.borrowedPercentBase
import com.urbanairship.android.layout.widget.establishesLength
import com.urbanairship.android.layout.widget.LengthBasisProvider
import com.urbanairship.android.layout.widget.ShrinkableView
import androidx.core.view.OnApplyWindowInsetsListener as OnApplyWindowInsetsListenerCompat

internal class ContainerLayoutView(
    context: Context,
    private val model: ContainerLayoutModel,
    private val viewEnvironment: ViewEnvironment
) : ClippableConstraintLayout(context), BaseView, ShrinkableView, AutoSizeProvider,
    LengthBasisProvider {

    private val frameShouldIgnoreSafeArea = SparseBooleanArray()
    private val frameMargins = SparseArray<Margin>()
    // frameId -> aspectRatio for height-auto + percent-width items. Their width is solved with the
    // other percent items; onMeasure derives the height from it here rather than leaving it to the
    // constraint set's dimension ratio, which resolves against the container height instead.
    private val frameHeightRatios = SparseArray<Double>()
    // frameId -> percent, for items sized as a percent of us on that axis. Only consulted when our
    // own size isn't fixed by our parent; otherwise ConstraintLayout resolves them natively.
    private val framePercentWidths = SparseArray<Float>()
    private val framePercentHeights = SparseArray<Float>()
    // frameId -> the width the percent solve gave it this pass. The ratio pass derives height from
    // this rather than from `measuredWidth`, which is a pass behind it when we resolve after
    // measuring. Not inferable from `lp.width`: MATCH_CONSTRAINT is 0, as is a resolved zero.
    private val resolvedFrameWidths = SparseArray<Int>()
    // Declared size paired with the view it was declared on, for the length-basis walk. Keyed off
    // the item rather than the frame: the frame's params are ours to rewrite during measurement,
    // while the declaration never changes.
    private val itemDeclarations = mutableListOf<Pair<Size, View>>()

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
        itemDeclarations.add(info.size to itemView)

        // Track items that need the 2-pass height fix (percent width + auto height + ratio). Their
        // width is still a percentage of us like any other, so they also go through the percent
        // solve below — only the height is ours to derive.
        val size = info.size
        if (size.aspectRatio != null && size.height.isAuto && size.width.isPercent) {
            frameHeightRatios.put(frameId, size.aspectRatio)
        }
        if (size.width.isPercent) framePercentWidths.put(frameId, size.width.getFloat())
        if (size.height.isPercent) framePercentHeights.put(frameId, size.height.getFloat())
    }

    private var isAutoWidth = false
    private var isAutoHeight = false

    override fun isAutoSized(horizontal: Boolean): Boolean =
        if (horizontal) isAutoWidth else isAutoHeight

    /**
     * Whether any item gives [horizontal] a length of its own, rather than a share of ours.
     *
     * Answered from the declarations, so a stack above us can tell whether we could ever supply the
     * length its percentages need. Auto defers to the content, so the question passes down.
     */
    override fun establishesLength(horizontal: Boolean): Boolean =
        itemDeclarations.any { (size, view) ->
            if (view.visibility == GONE) return@any false

            val dimension = if (horizontal) size.width else size.height
            when {
                dimension.isPercent -> false
                dimension.isAuto -> view.establishesLength(horizontal)
                else -> true
            }
        }

    /**
     * Whether any item takes exactly the whole of [horizontal].
     *
     * Separate from [establishesLength], which answers a parent asking whether we could supply the
     * length its percentages need — an item that is a share of us can never be that. This asks a
     * narrower question, about resolving our own items: we overlay rather than stack, so our size
     * on either axis is our largest item, and the whole of the largest is the largest. That settles
     * on the first pass where a fraction would halve away, so an item at the whole is worth
     * resolving against even when nothing else supplies a length.
     */
    private fun hasFullPercentItem(horizontal: Boolean): Boolean =
        itemDeclarations.any { (size, view) ->
            if (view.visibility == GONE) return@any false

            val dimension = if (horizontal) size.width else size.height
            dimension.isPercent && dimension.getFloat() == 1f
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        resolvedFrameWidths.clear()

        // Recorded before any child is measured, since that's when they ask. A fixed container is
        // a real ceiling and stops the search here; an auto one is only passing slack through.
        isAutoWidth = widthMode != MeasureSpec.EXACTLY
        isAutoHeight = heightMode != MeasureSpec.EXACTLY

        // Fixed by our parent: the base is the spec, known before anything is measured, so resolve
        // straight away in one pass. ConstraintLayout's own percent would size the item's content
        // and lay its margins outside it, which is a different rule from every other path here —
        // percentages cover the space an item occupies, margins included.
        if (widthMode == MeasureSpec.EXACTLY && framePercentWidths.size() > 0) {
            resolvePercentFrames(
                framePercentWidths,
                (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0),
                horizontal = true
            )
        }
        if (heightMode == MeasureSpec.EXACTLY && framePercentHeights.size() > 0) {
            resolvePercentFrames(
                framePercentHeights,
                (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0),
                horizontal = false
            )
        }

        // Anything not fixed needs a size worked out first, below.
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
        //
        // A borrowed viewport is a real length and settles the axis on its own. Failing that it
        // takes an item supplying a length to give the axis a size worth taking a percentage of —
        // an `AT_MOST` only says how much room we have, and an `UNSPECIFIED` not even that. Without
        // one the base would be 0 and every item would collapse.
        //
        // An item supplying a length is worth resolving against however we were measured, which is
        // what the stacks already do: `solveMainAxisLength` takes a borrowed viewport first and
        // otherwise solves from its own fixed content, bounded or not. Requiring `AT_MOST` here left
        // a percent item at its own content in any hierarchy that ran out of length basis above us,
        // which a `height: auto` modal is enough to do — its root stack has only a `100%` item to
        // solve from, so it measures this whole subtree unbounded and a `height: 100%` background
        // image stayed at the bitmap's intrinsic size.
        //
        // An item at exactly the whole counts too: it supplies no length, but 100% of our largest
        // item is that item, so resolving against the first pass settles instead of collapsing.
        // Without this, two `100% x 100%` items left each other with nothing to take a share of.
        // Bounded only, matching the stacks, where the no-solution case — percentages summing to
        // the whole or past it — likewise takes the bound only when there is a real one.
        val resolveWidths = autoWidths &&
                (borrowedWidth > 0 ||
                        establishesLength(horizontal = true) ||
                        (widthMode == MeasureSpec.AT_MOST && hasFullPercentItem(horizontal = true)))
        val resolveHeights = autoHeights &&
                (borrowedHeight > 0 ||
                        establishesLength(horizontal = false) ||
                        (heightMode == MeasureSpec.AT_MOST && hasFullPercentItem(horizontal = false)))

        if (frameHeightRatios.size() == 0 && !autoWidths && !autoHeights) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // First pass: measure percent items as auto. They can't resolve against a size we don't
        // have yet, and this clears any size left on them by an earlier pass.
        if (autoWidths) {
            forEachFrame(framePercentWidths) { it.width = LayoutParams.WRAP_CONTENT }
        }
        if (autoHeights) {
            forEachFrame(framePercentHeights) { it.height = LayoutParams.WRAP_CONTENT }
        }

        // Ratio items measure at WRAP_CONTENT height so the `"H,ratio:1"` the constraint set left on
        // them can't resolve: with a MATCH_CONSTRAINT dimension ConstraintLayout derives the *width*
        // from the container height, which ignores both the declared percent and our width. The
        // width comes from the percent solve like any other item, and the height from the ratio
        // pass below.
        for (i in 0 until frameHeightRatios.size()) {
            val frame = findViewById<View>(frameHeightRatios.keyAt(i)) ?: continue
            frame.applyParams { it.height = LayoutParams.WRAP_CONTENT }
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

            // The solved width when there was one, otherwise whatever the item measured to without
            // one — the same fallback a percent item with no ratio gets here, so the two agree.
            val naturalW = resolvedFrameWidths.get(frameId, NO_RESOLVED_WIDTH)
                .takeIf { it != NO_RESOLVED_WIDTH } ?: frame.measuredWidth
            val naturalH = (naturalW / ratio).roundToInt()

            val (correctW, correctH) = if (heightMode == MeasureSpec.EXACTLY && naturalH > containerH) {
                (containerH * ratio).roundToInt() to containerH
            } else {
                naturalW to naturalH
            }

            frame.applyParams { lp ->
                if (lp.width != correctW || lp.height != correctH) {
                    lp.width = correctW
                    lp.height = correctH
                    needsRemeasure = true
                }
            }
        }

        if (needsRemeasure) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * The size percent items resolve against on one axis: the viewport borrowed from a scroll
     * layout when we were measured unbounded, or otherwise the box we wrap to.
     *
     * That box is the extent of every item except the ones that are a *fraction* of us. A container
     * sized to its content can't take its size from a fraction of itself — `H = max(H * percent)`
     * has no solution but zero. An item at exactly the whole is not that: it is our largest item
     * whenever it is the largest, and its content is a length like any other. This is where iOS's
     * remeasure loop converges.
     */
    private fun percentBase(
        mode: Int,
        borrowed: Int,
        percents: SparseArray<Float>,
        horizontal: Boolean
    ): Int {
        // A borrowed viewport settles the axis outright. Without one, an unbounded spec carries no
        // size to fall back on, so the content box below is all there is — the same order
        // `solveMainAxisLength` takes it in.
        if (mode == MeasureSpec.UNSPECIFIED && borrowed > 0) return borrowed

        // Our non-percent items, plus any item at exactly the whole. We overlay rather than stack,
        // so our size is our largest item — and an item at the whole *is* that largest item
        // whenever it is the largest. Its first-pass measurement is its own content, a real length
        // like any other, so counting it settles rather than dividing.
        //
        // A fraction still can't decide us: resolving a 60% item against its own content would only
        // shrink it below that content and call the result stable.
        //
        // Counting the whole matters because leaving it out left a `100%` item resolving against
        // whichever non-percent sibling happened to be present, however small — the dismiss button
        // on a modal scene collapsed a full-page pager to 48pt.
        var base = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            // Absent from `percents` boxes to null: not a percent on this axis, so it counts.
            val percent = percents.get(child.id)
            if (percent != null && percent != 1f) continue
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
            frame.applyParams { lp ->
                val margins = if (horizontal) {
                    lp.leftMargin + lp.rightMargin
                } else {
                    lp.topMargin + lp.bottomMargin
                }
                // A percentage covers the space the item occupies, margins included, so they come
                // out of its share — the same rule the linear layout's slot distribution uses.
                val resolved = (minOf((percents.valueAt(i) * base).toInt(), base) - margins)
                    .coerceAtLeast(0)

                if (horizontal) resolvedFrameWidths.put(percents.keyAt(i), resolved)

                if (horizontal && lp.width != resolved) {
                    lp.width = resolved
                    changed = true
                } else if (!horizontal && lp.height != resolved) {
                    lp.height = resolved
                    changed = true
                }
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
            frame.applyParams(block)
        }
    }

    /**
     * Applies [block] to the frame's params, and tells the frame if they changed.
     *
     * Every size we solve here is written straight onto the params rather than through
     * `setLayoutParams`, so nothing marks the frame. ConstraintLayout copies params into its solver
     * in `setChildrenConstraints()`, which it only reaches from `updateHierarchy()` when one of its
     * *children* reports `isLayoutRequested` — a dirty flag on the container itself doesn't do it.
     * So on any pass where the children are already clean the solve is silently dropped and the
     * solver reuses the previous pass's dimensions.
     *
     * That is two bugs. A modal shrinking for the IME left its page laid out at the old height,
     * overflowing the top by the difference; and the bounded pass that corrects an unbounded one
     * never landed, so a `height: 100%` item kept whatever the unbounded pass had settled on.
     * ConstraintLayout's own source flags the shape — "window insets change may do that, we receive
     * a second onMeasure before onLayout".
     *
     * [View.forceLayout] rather than [View.requestLayout]: it sets the same flag `isLayoutRequested`
     * reads, without walking a layout request up the tree — not something to start from inside a
     * measure pass. Only on a real change, so a settled hierarchy still measures clean.
     */
    private inline fun View.applyParams(block: (ConstraintLayout.LayoutParams) -> Unit) {
        val lp = layoutParams as ConstraintLayout.LayoutParams
        val width = lp.width
        val height = lp.height
        block(lp)
        if (lp.width != width || lp.height != height) {
            forceLayout()
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

    private companion object {
        /** `SparseArray` default for "the percent solve didn't run for this frame". */
        const val NO_RESOLVED_WIDTH = -1
    }
}
