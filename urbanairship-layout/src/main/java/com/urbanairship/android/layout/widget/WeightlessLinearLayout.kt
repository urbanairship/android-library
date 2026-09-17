/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.util.hasAutoSizedAncestor
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.urbanairship.android.layout.util.isLayoutRtl

/** Variant of `LinearLayout` that replaces weight with max percentage sizes.*/
internal open class WeightlessLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr), AutoSizeProvider, LengthBasisProvider {

    enum class OrientationMode(private val rawValue: Int) {
        HORIZONTAL(0),
        VERTICAL(1);

        companion object {
            fun fromRawValue(rawValue: Int): OrientationMode? {
                return entries.firstOrNull { it.rawValue == rawValue }
            }
        }
    }

    internal var orientation = OrientationMode.HORIZONTAL
    internal var gravity = GravityCompat.START or Gravity.TOP

    private var totalLength = 0

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs, R.styleable.WeightlessLinearLayout, defStyleAttr, 0
        )
        ViewCompat.saveAttributeDataForStyleable(
            this, context, R.styleable.WeightlessLinearLayout, attrs, typedArray, defStyleAttr, 0
        )

        var index = typedArray.getInt(R.styleable.WeightlessLinearLayout_android_orientation, -1)
        setOrientation(OrientationMode.fromRawValue(index))

        index = typedArray.getInt(R.styleable.WeightlessLinearLayout_android_gravity, -1)
        if (index >= 0) {
            setGravity(index)
        }

        typedArray.recycle()
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    /**
     * Should the layout be a column or a row.
     * @param orientation Pass [OrientationMode.HORIZONTAL] or [OrientationMode.VERTICAL]. Default
     * value is [OrientationMode.HORIZONTAL].
     */
    fun setOrientation(orientation: OrientationMode?) {
        if (this.orientation == orientation || orientation == null) {
            return
        }

        this.orientation = orientation
        requestLayout()
    }

    /**
     * Returns the current orientation.
     *
     * @return the current orientation. See [OrientationMode]
     */
    fun getOrientation(): OrientationMode {
        return orientation
    }

    /**
     * Describes how the child views are positioned. Defaults to GRAVITY_TOP. If
     * this layout has a VERTICAL orientation, this controls where all the child
     * views are placed if there is extra vertical space. If this layout has a
     * HORIZONTAL orientation, this controls the alignment of the children.
     *
     * @param gravity See [Gravity]
     */
    fun setGravity(gravity: Int) {
        if (this.gravity == gravity) {
            return
        }

        var corrected = gravity
        if ((corrected and GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
            corrected = corrected or GravityCompat.START
        }

        if ((corrected and Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            corrected = corrected or Gravity.TOP
        }

        this.gravity = corrected
        requestLayout()
    }

    /**
     * Returns the current gravity. See [Gravity]
     *
     * @return the current gravity.
     * @see .setGravity
     */
    fun getGravity(): Int {
        return gravity
    }

    fun setHorizontalGravity(horizontalGravity: Int) {
        val gravity = horizontalGravity and GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK
        val updated = this.gravity and GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK
        if (updated != gravity) {
            this.gravity =
                (this.gravity and GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK.inv()) or gravity
            requestLayout()
        }
    }

    fun setVerticalGravity(verticalGravity: Int) {
        val gravity = verticalGravity and Gravity.VERTICAL_GRAVITY_MASK
        if ((this.gravity and Gravity.VERTICAL_GRAVITY_MASK) != gravity) {
            this.gravity = (this.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or gravity
            requestLayout()
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    /**
     * Returns a set of layout parameters with a width of
     * [ViewGroup.LayoutParams.MATCH_PARENT]
     * and a height of [ViewGroup.LayoutParams.WRAP_CONTENT]
     * when the layout's orientation is [OrientationMode.VERTICAL]. When the orientation is
     * [OrientationMode.HORIZONTAL], the width is set to [android.view.ViewGroup.LayoutParams.WRAP_CONTENT]
     * and the height to [android.view.ViewGroup.LayoutParams.WRAP_CONTENT].
     */
    override fun generateDefaultLayoutParams(): LayoutParams? {
        return when(orientation) {
            OrientationMode.HORIZONTAL -> LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            OrientationMode.VERTICAL -> LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = ACCESSIBILITY_CLASS_NAME
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = ACCESSIBILITY_CLASS_NAME
    }

    private var isAutoWidth = false
    private var isAutoHeight = false

    override fun isAutoSized(horizontal: Boolean): Boolean =
        if (horizontal) isAutoWidth else isAutoHeight

    /**
     * Whether any child gives [horizontal] a length of its own, rather than a share of ours.
     *
     * Auto defers to the content, so the question passes down: a stack of percentages is no more
     * able to supply a length than a percentage is, however many wrappers sit in between.
     */
    override fun establishesLength(horizontal: Boolean): Boolean {
        for (i in 0..<childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue

            val lp = child.layoutParams as? LayoutParams ?: return true
            val declared = if (horizontal) lp.width else lp.height
            val percent = if (horizontal) lp.maxWidthPercent else lp.maxHeightPercent

            val establishes = when {
                // A share of us, so it can't be what decides us.
                declared == 0 && percent > 0f -> false
                declared == ViewGroup.LayoutParams.MATCH_PARENT -> false
                declared == ViewGroup.LayoutParams.WRAP_CONTENT -> child.establishesLength(horizontal)
                // A fixed length in pixels.
                else -> true
            }

            if (establishes) return true
        }
        return false
    }

    /**
     * Whether a child is one the shrink pass can take an overflow out of.
     *
     * A child holding a length of its own has nothing to give: the length is what it was told to
     * be. What is left is the content-sized children that also say they can be smaller.
     *
     * A stack gives only what its own children can. One that holds a stated length reports that
     * length however little room it is handed, so the difference comes off as content cut away
     * rather than content scaled down — a fixed-height media band clipped to pay for its siblings.
     */
    private fun View.givesOnShrink(horizontal: Boolean): Boolean {
        val lp = layoutParams as? LayoutParams ?: return false
        val declared = if (horizontal) lp.width else lp.height
        val percent = if (horizontal) lp.maxWidthPercent else lp.maxHeightPercent

        if (declared != ViewGroup.LayoutParams.WRAP_CONTENT || percent != 0f || lp.aspectRatio != 0f) {
            return false
        }
        if (this !is ShrinkableView || !isShrinkable()) return false

        return this !is WeightlessLinearLayout ||
                (0..<childCount).any { getChildAt(it)?.givesOnShrink(horizontal) == true }
    }

    /**
     * Whether any child takes exactly the whole of [horizontal] as a percentage.
     *
     * Only meaningful on our cross axis, where our length is the widest child rather than the sum:
     * the whole of the widest IS the widest, so handing the measurement back settles where it
     * started instead of walking. It is what makes a column of full-width buttons line up with each
     * other rather than each hugging its own label.
     *
     * Anything below the whole still halves away to nothing, so it doesn't count, and the main axis
     * never asks — children sum there, and `n` children at the whole diverge.
     */
    private fun hasFullPercentChild(horizontal: Boolean): Boolean {
        for (i in 0..<childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue

            val lp = child.layoutParams as? LayoutParams ?: continue
            val declared = if (horizontal) lp.width else lp.height
            val percent = if (horizontal) lp.maxWidthPercent else lp.maxHeightPercent

            if (declared == 0 && percent == 1f) return true
        }
        return false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Recorded before any child is measured, since that's when they ask. Anything short of
        // EXACTLY leaves us sizing to our content, so whatever ceiling we pass down is slack.
        isAutoWidth = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY
        isAutoHeight = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY

        when(orientation) {
            OrientationMode.HORIZONTAL -> measureHorizontal(widthMeasureSpec, heightMeasureSpec)
            OrientationMode.VERTICAL -> measureVertical(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        when(orientation) {
            OrientationMode.HORIZONTAL -> layoutHorizontal(l, t, r, b)
            OrientationMode.VERTICAL -> layoutVertical(l, t, r, b)
        }
    }

    /**
     * Helper for measuring children when in `VERTICAL` orientation.
     *
     * @param widthMeasureSpec width spec from parent view.
     * @param heightMeasureSpec height spec from parent view.
     */
    private fun measureVertical(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        totalLength = 0
        var maxWidth = 0
        var childState = 0
        var alternativeMaxWidth = 0
        var percentMaxWidth = 0
        var allFillParent = true

        val count = childCount
        val childrenWithMaxPercent = mutableListOf<View>()
        val ratioFillChildren = mutableListOf<View>()
        // Percent on our cross axis, deferred until we know how wide we ended up.
        val crossAxisPercentChildren = mutableListOf<View>()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        // `P` — read from the layout params, so it's known before anything is measured.
        val percentTotal = mainAxisPercentTotal(vertical = true)

        // Whether anything gives our CROSS axis a length that isn't a share of it. Keeping percent
        // children out of `maxWidth` is right while something else sets the width; when nothing
        // does it leaves us zero wide, and a share of zero is zero — the children disappear, and
        // the stack with them. No basis is the same situation the main axis already handles by
        // letting the percentages fall back to their content, so answer it the same way here.
        //
        // Lazy because answering walks the subtree, and only a stack that actually has a cross-axis
        // percent child ever asks. Unsynchronized: measurement is the main thread's.
        val crossAxisHasBasis: Boolean by lazy(LazyThreadSafetyMode.NONE) {
            widthMode == MeasureSpec.EXACTLY || establishesLength(horizontal = true)
        }

        // A child at exactly 100% is a third case, between the two above. It supplies no width, so
        // it belongs in `maxWidth` like any other basis-less percent child — but unlike a fraction
        // it settles rather than walks, so it still gets its share of the width it helped set, and
        // a column of full-width rows ends up as wide as its longest one.
        val crossAxisSharesTheWhole: Boolean by lazy(LazyThreadSafetyMode.NONE) {
            hasFullPercentChild(horizontal = true)
        }

        var matchWidth = false
        var skippedMeasure = false
        // What the deferred ratio children will take on our axis, accumulated as we skip past them.
        var idealRatioLength = 0

        // What the children that can't give have taken, which is what the rest have to share.
        var fixedLength = 0

        // What the children that can bend are allowed when a percent sibling wants the same room.
        // Settled once the children that can't give are measured, and null when nothing needs
        // rationing.
        var shares: Shares? = null

        // See how tall everyone is. Also remember max width.
        //
        // Children that can give are measured after the ones that can't, and all against the same
        // room: what a stack has to divide is what is left once its fixed content is in. Measured
        // in order, the first to ask takes as much as it likes and anything after it — another
        // image, or the paragraph under them both — is measured against nothing and disappears.
        for (pass in 0..1) {
            if (pass == 1) {
                fixedLength = totalLength
                if (percentTotal > 0f) {
                    shares = sharesWithPercentChildren(
                        vertical = true,
                        fixedLength = fixedLength,
                        mainMeasureSpec = heightMeasureSpec,
                        crossMeasureSpec = widthMeasureSpec
                    )
                }
            }

        for (i in 0..<count) {
            val child = getChildAt(i) ?: continue

            if (child.visibility == GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams

            if (child.givesOnShrink(horizontal = false) != (pass == 1)) continue

            // Any height-auto + ratio in a bounded layout: defer to a third pass so ratio items
            // never overflow the space remaining after siblings are measured. Their length still
            // counts toward `S` below — it's derived from our width, which is settled before the
            // height solve runs, so a ratio child is fixed content as far as the solve is
            // concerned. Leaving it out made a percent sibling a fraction of a length the stack
            // never had: 120 fixed + 50% + a 108 ratio row rendered the 50% row at 120 of 348.
            if (lp.aspectRatio > 0f
                && lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxHeightPercent == 0f
                && (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST)) {
                ratioFillChildren.add(child)
                idealRatioLength +=
                    ratioMainExtent(lp, vertical = true, crossSpecSize = widthSize) +
                            lp.topMargin + lp.bottomMargin
                allFillParent = false
                continue
            }

            if (lp.maxHeightPercent > 0) {
                childrenWithMaxPercent.add(child)
            }

            if (lp.height == 0 && lp.maxHeightPercent > 0) {
                // Don't measure percent children yet, and don't let them into the sum: a percent
                // child is a fraction of a length it also contributes to, so including it here
                // would make the percentage describe something other than the finished stack.
                // Only its margins count — they're fixed content. What's left in totalLength is
                // `S` for the `H = S / (1 - P)` solve below, and these children get sized in the
                // distribution pass once there's a length to resolve against.
                val totalLength = this.totalLength
                this.totalLength = max(totalLength, totalLength + lp.topMargin + lp.bottomMargin)
                skippedMeasure = true
            } else {
                val childHorizontalMargins = lp.marginStart + lp.marginEnd
                var oldWidth = Int.MIN_VALUE
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    oldWidth = 0
                    lp.width = if (widthMode == MeasureSpec.EXACTLY) {
                        // Our width is settled; resolve against it directly.
                        ((widthSize * lp.maxWidthPercent).toInt() - childHorizontalMargins)
                            .coerceAtLeast(0)
                    } else {
                        // We're sized by our content, and a child that's a fraction of our width
                        // can't be part of what decides it. Measure it at its own content for now
                        // and give it its share once our width is known.
                        //
                        // This also avoids resolving against `widthSize`, which is 0 under an
                        // UNSPECIFIED spec: the old arithmetic went negative and aliased into
                        // MATCH_PARENT or WRAP_CONTENT depending on how many pixels of margin the
                        // child happened to have.
                        crossAxisPercentChildren.add(child)
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }

                // Determine how big this child would like to be, in the space its siblings left.
                // Withheld only when percent children are in play: their slots come out of the
                // distribution pass below, so the length is still moving and subtracting it here
                // would measure everyone after them against a budget that hasn't settled. This is
                // `LinearLayout`'s own rule, where weights stand in for percentages.
                // A child that can give measures against the whole of our length rather than
                // what its siblings left: what it asks for is what the shrink below divides
                // between them. Against the leftovers the first to ask takes the room and the
                // last is handed what remains, however little that is.
                val share = shares?.perChild?.get(i) ?: -1
                val heightUsed = when {
                    // Withhold all but the share, pinning the child to exactly that height. A
                    // percent sibling's claim has already been taken out of it.
                    share >= 0 -> (MeasureSpec.getSize(heightMeasureSpec) - paddingTop
                            - paddingBottom - lp.topMargin - lp.bottomMargin - share)
                        .coerceAtLeast(0)
                    percentTotal != 0f -> 0
                    pass == 1 -> fixedLength
                    else -> totalLength
                }

                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    childHorizontalMargins,
                    heightMeasureSpec,
                    heightUsed
                )

                if (oldWidth != Int.MIN_VALUE) {
                    lp.width = oldWidth
                }

                if (lp.aspectRatio > 0f) {
                    remeasureWithAspectRatio(child, lp, widthMeasureSpec, heightMeasureSpec)
                }

                val childHeight = child.measuredHeight
                val totalLength = this.totalLength
                this.totalLength =
                    max(totalLength, totalLength + childHeight + lp.topMargin + lp.bottomMargin)
            }

            var matchWidthLocally = false
            if (widthMode != MeasureSpec.EXACTLY && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                // The width of the linear layout will scale, and at least one child said it wanted to match our width.
                // Set a flag indicating that we need to remeasure at least that view when we know our width.
                matchWidth = true
                matchWidthLocally = true
            }

            val margin = lp.marginStart + lp.marginEnd
            val measuredWidth = child.measuredWidth + margin
            childState = combineMeasuredStates(childState, child.measuredState)

            // A child that's a fraction of our width doesn't get a say in what our width is —
            // otherwise the percentage would end up describing a length it set itself. Unless it
            // is all we have: with no basis the percentage is never taken, so what it measured is
            // its content and counts like anyone else's.
            if (child !in crossAxisPercentChildren || !crossAxisHasBasis) {
                maxWidth = max(maxWidth, measuredWidth)

                if (lp.maxHeightPercent > 0) {
                    // Widths of max percentage Views are bogus if we end up remeasuring, so keep them separate.
                    percentMaxWidth =
                        max(percentMaxWidth, if (matchWidthLocally) margin else measuredWidth)
                } else {
                    alternativeMaxWidth =
                        max(alternativeMaxWidth, if (matchWidthLocally) margin else measuredWidth)
                }
            }

            allFillParent = allFillParent && lp.width == ViewGroup.LayoutParams.MATCH_PARENT
        }
        }

        // Add in our padding
        totalLength += paddingTop + paddingBottom

        // `S` — the fixed content: everything measured above plus what the deferred ratio children
        // will take, less the percent children's margins, which the distribution pass takes out of
        // their own slots rather than up front.
        val percentMargins = childrenWithMaxPercent.sumOf {
            val lp = it.layoutParams as LayoutParams
            lp.topMargin + lp.bottomMargin
        }

        // Check against our minimum height
        val solvedHeight = solveMainAxisLength(
            mode = heightMode,
            specSize = MeasureSpec.getSize(heightMeasureSpec),
            fixedLength = totalLength - percentMargins + idealRatioLength,
            percentTotal = percentTotal,
            borrowed = if (percentTotal > 0f) borrowedPercentBase(horizontal = false) else 0,
            autoAncestor = percentTotal >= 1f && hasAutoSizedAncestor(horizontal = false),
            // Only asked when we're about to solve — it walks the subtree.
            hasLengthBasis = percentTotal <= 0f || establishesLength(horizontal = false)
        )
        var height = solvedHeight ?: totalLength

        // Whether there's a length for percent children to be a fraction of. An EXACTLY spec is one
        // without needing a solve; otherwise it's whatever we solved for, and nothing means the
        // percent children measure at their content instead.
        val hasResolvedHeight = heightMode == MeasureSpec.EXACTLY || solvedHeight != null
        height = max(height, suggestedMinimumHeight)

        // Reconcile our calculated size with the heightMeasureSpec
        var heightSizeAndState = resolveSizeAndState(height, heightMeasureSpec, 0)
        height = heightSizeAndState and MEASURED_SIZE_MASK

        // Either expand children with percentage dimensions to take up available space or shrink them if they extend
        // beyond our current bounds.
        // Unbounded: `height` is a base to resolve against, not a length to divide up, so hand the
        // distribution exactly what the percent children add up to and let them overflow us.
        var delta = if (heightMode == MeasureSpec.UNSPECIFIED && solvedHeight != null) {
            (height * percentTotal).roundToInt() - percentMargins
        } else {
            height - totalLength
        }

        // Whether a child's height below is an allowance we handed it rather than something it
        // measured. The cross-axis pass re-measures heights, and must not hand back the space the
        // overflow branch just took away.
        var heightsWereRationed = delta < 0

        // If the delta is negative, the content is too big for the layout. We need to find
        // children with wrap_content height that we can shrink.
        if (delta < 0) {
            val shrinkableChildren = mutableListOf<View>()
            val originalHeights = mutableListOf<Int>()
            var totalShrinkableHeight = 0

            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE) {
                    continue
                }

                if (child.givesOnShrink(horizontal = false)) {
                    shrinkableChildren.add(child)

                    val childHeight = child.measuredHeight
                    originalHeights.add(childHeight)
                    totalShrinkableHeight += childHeight
                }
            }

            if (!shrinkableChildren.isEmpty() && totalShrinkableHeight >= abs(delta)) {
                fun widthSpecFor(child: View): Int {
                    val lp = child.layoutParams as LayoutParams
                    return when {
                        lp.width == 0 && lp.maxWidthPercent > 0 ->
                            if (widthMode == MeasureSpec.EXACTLY) {
                                val childWidth =
                                    ((widthSize * lp.maxWidthPercent).toInt() - lp.marginStart - lp.marginEnd)
                                        .coerceAtLeast(0)
                                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                            } else {
                                // Our width isn't settled, so keep what this measured; the
                                // cross-axis pass gives it its share once we know what we ended up.
                                MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY)
                            }

                        lp.width == ViewGroup.LayoutParams.MATCH_PARENT && widthMode != MeasureSpec.EXACTLY ->
                            MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY)

                        else -> getChildMeasureSpec(
                            widthMeasureSpec, lp.marginStart + lp.marginEnd, lp.width
                        )
                    }
                }

                // Each gives in proportion to what it asked for, and `AT_MOST` lets it answer with
                // what it can actually be: a child that holds a length inside it reports that
                // instead, and the round after shares what it couldn't give among the rest.
                // Forcing the share on it would clip its content rather than shrink it.
                var owed = -delta
                var giving: List<View> = shrinkableChildren
                while (owed > 0 && giving.isNotEmpty()) {
                    val asked = giving.sumOf { it.measuredHeight }
                    if (asked <= 0) break

                    val shrinkRatio = ((asked - owed).toFloat() / asked).coerceAtLeast(0f)
                    val stillGiving = mutableListOf<View>()
                    for (child in giving) {
                        val was = child.measuredHeight
                        // Floored, so the rounding can only leave a pixel spare rather than push
                        // the stack over the length it is dividing.
                        val share = max(0, (was * shrinkRatio).toInt())

                        child.measure(
                            widthSpecFor(child),
                            MeasureSpec.makeMeasureSpec(share, MeasureSpec.AT_MOST)
                        )

                        owed -= was - child.measuredHeight
                        if (child.measuredHeight <= share) stillGiving.add(child)
                    }

                    // Nobody had anything more to give, or everyone gave their share.
                    if (stillGiving.size == giving.size) break
                    giving = stillGiving
                }

                // Recalculate totalLength, and our width with it: a child that gave something
                // back may be narrower for it, and our width was taken from what it asked for.
                // Held from before, a stack hugging an image that has since scaled down keeps its
                // old width and shows as background either side of the image.
                totalLength = 0
                maxWidth = 0
                percentMaxWidth = 0
                alternativeMaxWidth = 0
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) {
                        continue
                    }

                    val lp = child.layoutParams as LayoutParams
                    totalLength += child.measuredHeight + lp.topMargin + lp.bottomMargin

                    val margin = lp.marginStart + lp.marginEnd
                    val matchWidthLocally = widthMode != MeasureSpec.EXACTLY &&
                            lp.width == ViewGroup.LayoutParams.MATCH_PARENT
                    val childWidth = if (matchWidthLocally) margin else child.measuredWidth + margin

                    if (child !in crossAxisPercentChildren || !crossAxisHasBasis) {
                        maxWidth = max(maxWidth, child.measuredWidth + margin)

                        if (lp.maxHeightPercent > 0) {
                            percentMaxWidth = max(percentMaxWidth, childWidth)
                        } else {
                            alternativeMaxWidth = max(alternativeMaxWidth, childWidth)
                        }
                    }
                }
                totalLength += paddingTop + paddingBottom

                // Delta should now be close to zero
                delta = height - totalLength
            } else {
                // Fixed children overflow the parent, and WRAP_CONTENT children can't absorb it;
                // zero out all WRAP_CONTENT children to match iOS failure behavior.
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) continue
                    val lp = child.layoutParams as LayoutParams
                    if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxHeightPercent == 0f) {
                        val widthSpec = getChildMeasureSpec(
                            widthMeasureSpec, lp.marginStart + lp.marginEnd, lp.width
                        )
                        child.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY))
                    }
                }
                totalLength = 0
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) continue
                    val lp = child.layoutParams as LayoutParams
                    totalLength += child.measuredHeight + lp.topMargin + lp.bottomMargin
                }
                totalLength += paddingTop + paddingBottom
                delta = height - totalLength
            }
        }

        // Measure any skipped children or distribute leftover space to percentage-width children.
        if (skippedMeasure || (delta != 0 && !childrenWithMaxPercent.isEmpty())) {
            Collections.sort(childrenWithMaxPercent, Comparator { v1: View, v2: View ->
                val p1 = (v1.layoutParams as LayoutParams).maxHeightPercent
                val p2 = (v2.layoutParams as LayoutParams).maxHeightPercent
                p1.compareTo(p2)
            })

            val maxPercentCount = childrenWithMaxPercent.size

            // Distribute equal slots across percent children (iOS HStack/VStack-style fair
            // share), then subtract each child's own margin from its slot to get content
            // height. A margined child ends up shorter than its unmargined siblings, instead
            // of every percent child getting equal content while margins pile on top.
            val percentMargins = childrenWithMaxPercent.sumOf {
                val marginLp = it.layoutParams as LayoutParams
                marginLp.topMargin + marginLp.bottomMargin
            }
            // Less what the ratio children are owed: they are measured after this, against what is
            // left, so anything handed out here is taken from them.
            var slotRemaining =
                (delta + percentMargins - (shares?.ratioReserve ?: 0)).coerceAtLeast(0)

            val lastChildIndex = maxPercentCount - 1
            for (i in 0..<maxPercentCount) {
                val child = childrenWithMaxPercent[i]
                val lp = child.layoutParams as LayoutParams

                val widthSpec: Int
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    widthSpec = if (widthMode == MeasureSpec.EXACTLY) {
                        // Our width is settled; resolve against it directly.
                        val childWidth =
                            ((widthSize * lp.maxWidthPercent).toInt() - lp.marginStart - lp.marginEnd)
                                .coerceAtLeast(0)
                        MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                    } else {
                        // The same bargain the first pass makes. `widthSize` is a ceiling we may not
                        // fill, so measure at content now and hand out the share once our width is
                        // known. Without this a child that's a percent on both axes resolved its
                        // width against the ceiling while one that's a percent on the cross axis
                        // alone resolved against our width — two answers for one declaration.
                        crossAxisPercentChildren.add(child)
                        getChildMeasureSpec(
                            widthMeasureSpec,
                            paddingStart + paddingEnd + lp.marginStart + lp.marginEnd,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                } else {
                    widthSpec = getChildMeasureSpec(
                        widthMeasureSpec,
                        paddingStart + paddingEnd + lp.marginStart + lp.marginEnd,
                        lp.width
                    )
                }

                // An unbounded spec is fine to distribute against as long as we solved a base for
                // it — a borrowed viewport, or `S / (1 - P)`. Without one there's nothing to take a
                // percentage of, so fall back to the child's own content rather than leaving it
                // unmeasured, matching iOS, where a percent with no parent size behaves as auto.
                if (hasResolvedHeight) {
                    val remaining = maxPercentCount - i

                    val actualPercent: Float = when {
                        slotRemaining >= (height * lp.maxHeightPercent) * remaining ->
                            lp.maxHeightPercent
                        height > 0 ->
                            slotRemaining.toFloat() / remaining.toFloat() / height.toFloat()
                        // height == 0: slot collapses to 0 below regardless, avoid NaN.
                        else -> 0f
                    }

                    // When delta < 0 siblings already overflow the parent; give each percent
                    // child 0px to match iOS failure behavior (fixed children win).
                    var slot = if (delta >= 0) {
                        (actualPercent * height).toInt()
                    } else {
                        0
                    }
                    if (i == lastChildIndex && delta >= 0) {
                        slot = min(slot, slotRemaining)
                    }

                    slotRemaining -= slot

                    val childMargin = lp.topMargin + lp.bottomMargin
                    val childHeight = (slot - childMargin).coerceAtLeast(0)

                    val heightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                    child.measure(widthSpec, heightSpec)

                    if (lp.aspectRatio > 0f) {
                        remeasureWithAspectRatio(child, lp, widthMeasureSpec, heightMeasureSpec)
                    }

                    // Child may now not fit in vertical dimension.
                    childState = combineMeasuredStates(
                        childState,
                        child.measuredState and (MEASURED_STATE_MASK shr MEASURED_HEIGHT_STATE_SHIFT)
                    )
                } else {
                    child.measure(
                        widthSpec,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    )
                    childState = combineMeasuredStates(childState, child.measuredState)
                }
            }

            // Determine width now that all views have been measured. Rebuild the length from
            // scratch: it still holds the fixed content from the first pass, and every child is
            // about to be counted again — including the ones already in there.
            totalLength = 0
            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE) {
                    continue
                }

                val lp = child.layoutParams as LayoutParams
                val margin = lp.marginStart + lp.marginEnd
                val measuredWidth = child.measuredWidth + margin

                // Same rule as the first pass, and the same exception: a child that's a fraction of
                // our width doesn't get a say in what our width is, unless nothing else supplies
                // one. Leaving it out there but counting it here made our width depend on whether
                // some *other* child happened to have a main-axis percent, since that's the only
                // thing that brings us into this loop.
                //
                // A child that's a percent on both axes only joins the set in this pass, so without
                // the exception here it could still leave `maxWidth` at zero.
                if (child !in crossAxisPercentChildren || !crossAxisHasBasis) {
                    maxWidth = max(maxWidth, measuredWidth)

                    val matchWidthLocally = widthMode != MeasureSpec.EXACTLY &&
                            lp.width == ViewGroup.LayoutParams.MATCH_PARENT

                    alternativeMaxWidth =
                        max(alternativeMaxWidth, if (matchWidthLocally) margin else measuredWidth)
                }

                allFillParent = allFillParent && lp.width == ViewGroup.LayoutParams.MATCH_PARENT

                val totalLength = this.totalLength
                this.totalLength = max(
                    totalLength,
                    totalLength + child.measuredHeight + lp.topMargin + lp.bottomMargin
                )
            }

            totalLength += paddingTop + paddingBottom
        } else {
            alternativeMaxWidth = max(alternativeMaxWidth, percentMaxWidth)
        }

        // Third pass: measure deferred ratio children using the remaining available height so they
        // never push siblings off screen. For AT_MOST parents the bound is the spec size minus what
        // siblings consumed; for EXACTLY the bound is what's left of the fixed parent height.
        if (ratioFillChildren.isNotEmpty()) {
            val boundedParentH = if (heightMode == MeasureSpec.AT_MOST)
                MeasureSpec.getSize(heightMeasureSpec) else height

            // What everyone else ended up taking, percent slots included. The first-pass total
            // stood in for this before, which counted the percent children's margins but not the
            // slots they were given, so the ratio children were offered space already spoken for.
            var consumed = paddingTop + paddingBottom
            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE || child in ratioFillChildren) continue
                val lp = child.layoutParams as LayoutParams
                consumed += child.measuredHeight + lp.topMargin + lp.bottomMargin
            }
            val remaining = (boundedParentH - consumed).coerceAtLeast(0)

            // Per-child ideal sizes (idealW = column/declared width, idealH = idealW / ratio).
            val idealWs = IntArray(ratioFillChildren.size)
            val idealHs = IntArray(ratioFillChildren.size)
            for (i in ratioFillChildren.indices) {
                val lp = ratioFillChildren[i].layoutParams as LayoutParams
                idealWs[i] = ratioCrossExtent(lp, vertical = true, crossSpecSize = widthSize)
                idealHs[i] = (idealWs[i] / lp.aspectRatio).toInt()
            }

            // Greedy fair-share of the remaining height, SwiftUI-style: process
            // children from smallest ideal height to largest, giving each the
            // lesser of its natural height and its share of what's left.
            val childHeights = IntArray(ratioFillChildren.size)
            val order = ratioFillChildren.indices.sortedBy { idealHs[it] }
            var heightLeft = remaining
            var slotsLeft = ratioFillChildren.size
            for (i in order) {
                val lp = ratioFillChildren[i].layoutParams as LayoutParams
                val mainMargin = lp.topMargin + lp.bottomMargin
                val slot = (heightLeft / slotsLeft - mainMargin).coerceAtLeast(0)
                val childH = min(idealHs[i], slot)
                childHeights[i] = childH
                heightLeft -= (childH + mainMargin)
                slotsLeft -= 1
            }

            for (i in ratioFillChildren.indices) {
                val child = ratioFillChildren[i]
                val lp = child.layoutParams as LayoutParams

                val childH = childHeights[i]
                // Derive width to preserve the ratio (matches .aspectRatio(.fit)).
                val childW = min(idealWs[i], (childH * lp.aspectRatio).toInt()).coerceAtLeast(0)

                child.measure(
                    MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
                )
                childState = combineMeasuredStates(childState, child.measuredState)

                val measuredWidth = child.measuredWidth + lp.marginStart + lp.marginEnd
                maxWidth = max(maxWidth, measuredWidth)
                alternativeMaxWidth = max(alternativeMaxWidth, measuredWidth)
            }
        }

        if (!allFillParent && widthMode != MeasureSpec.EXACTLY) {
            maxWidth = alternativeMaxWidth
        }

        // Check against our minimum width
        maxWidth += paddingStart + paddingEnd
        maxWidth = max(maxWidth, suggestedMinimumWidth)

        val widthSizeAndState = resolveSizeAndState(maxWidth, widthMeasureSpec, childState)

        // Now that our width is settled, give the deferred cross-axis percent children their share
        // of it. A child that's auto on the main axis is measured afresh rather than pinned to what
        // it measured while wide: a label that fitted on one line at its content width needs more
        // lines once it's narrowed, and pinning the old height clipped exactly that text. A child
        // holding a distributed slot keeps it — that height is its share, not a measurement — and so
        // does everyone if the overflow branch already rationed the space.
        //
        // Skipped when there is no basis and nothing takes the whole: our width came from these
        // children, so handing them a share of it is handing them a share of themselves. A child at
        // the whole is the exception — that share is the width it already set, so giving it out
        // changes nothing except to bring its siblings up to the same edge.
        if (crossAxisPercentChildren.isNotEmpty() && (crossAxisHasBasis || crossAxisSharesTheWhole)) {
            val available = ((widthSizeAndState and MEASURED_SIZE_MASK) - paddingStart - paddingEnd)
                .coerceAtLeast(0)
            for (child in crossAxisPercentChildren) {
                val lp = child.layoutParams as LayoutParams
                val margins = lp.marginStart + lp.marginEnd
                val childWidth = (minOf((available * lp.maxWidthPercent).toInt(), available) - margins)
                    .coerceAtLeast(0)

                val hasSlot = lp.height == 0 && lp.maxHeightPercent > 0
                val heightSpec = if (hasSlot || heightsWereRationed) {
                    MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
                } else {
                    getChildMeasureSpec(
                        heightMeasureSpec,
                        paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                        lp.height
                    )
                }

                child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), heightSpec
                )
                childState = combineMeasuredStates(childState, child.measuredState)
            }
        }

        // Every child has a final height now — distributed slots, ratio rows, and the cross-axis
        // children just re-measured. Rebuild the length from all of them so what we report and what
        // `onLayout` positions against agree. The ratio pass used to report the first-pass total
        // plus the ratio rows, which left the percent slots out entirely, so the stack drew shorter
        // than its own children and they spilled past it.
        if (percentTotal > 0f || ratioFillChildren.isNotEmpty() ||
            crossAxisPercentChildren.isNotEmpty()
        ) {
            totalLength = paddingTop + paddingBottom
            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE) continue
                val lp = child.layoutParams as LayoutParams
                totalLength += child.measuredHeight + lp.topMargin + lp.bottomMargin
            }

            // Unless the spec fixed our height, report the content — in particular, when the base
            // was a borrowed viewport we may well be taller than it, which is the whole point of
            // being inside a scroll.
            if (heightMode != MeasureSpec.EXACTLY) {
                heightSizeAndState = resolveSizeAndState(totalLength, heightMeasureSpec, childState)
            }
        }

        setMeasuredDimension(widthSizeAndState, heightSizeAndState)

        if (matchWidth) {
            forceUniformWidth(count, heightMeasureSpec)
        }
    }

    private fun forceUniformWidth(count: Int, heightMeasureSpec: Int) {
        // Pretend that the linear layout has an exact size.
        val uniformMeasureSpec =
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        for (i in 0..<count) {
            val child = getChildAt(i)
            if (child.visibility == GONE) { continue }

            val lp = child.layoutParams as LayoutParams

            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                // Temporarily force children to reuse their old measured height
                val oldHeight = lp.height
                lp.height = child.measuredHeight

                // Remeasure with new dimensions
                measureChildWithMargins(child, uniformMeasureSpec, 0, heightMeasureSpec, 0)
                lp.height = oldHeight
            }
        }
    }

    /**
     * Helper for measuring children when in `HORIZONTAL` orientation.
     *
     * @param widthMeasureSpec width spec from parent view.
     * @param heightMeasureSpec height spec from parent view.
     */
    private fun measureHorizontal(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        totalLength = 0
        var maxHeight = 0
        var childState = 0
        var alternativeMaxHeight = 0
        var percentMaxHeight = 0
        var allFillParent = true

        val count = childCount
        val childrenWithMaxPercent = mutableListOf<View>()
        val ratioFillChildren = mutableListOf<View>()
        // Percent on our cross axis, deferred until we know how tall we ended up.
        val crossAxisPercentChildren = mutableListOf<View>()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // The vertical twin of the check in `measureVertical`: our cross axis is height here, and a
        // lone `height: 50%` child in an auto-height row collapses the same way a `width: 50%` one
        // did in an auto-width column. Same laziness, for the same reason.
        val crossAxisHasBasis: Boolean by lazy(LazyThreadSafetyMode.NONE) {
            heightMode == MeasureSpec.EXACTLY || establishesLength(horizontal = false)
        }

        // As in `measureVertical`: a child at exactly the whole settles rather than walks, so it
        // still gets its share and a row of full-height columns ends up as tall as its tallest.
        val crossAxisSharesTheWhole: Boolean by lazy(LazyThreadSafetyMode.NONE) {
            hasFullPercentChild(horizontal = false)
        }

        // `P` — read from the layout params, so it's known before anything is measured.
        val percentTotal = mainAxisPercentTotal(vertical = false)

        // Equal shares for a row of nothing but `auto` children, settled before anyone is measured
        // for real so that no child's width depends on where the loop had got to when it was
        // reached. Null when the row isn't one of those, and everyone is paid in order as before.
        val shares = if (sharesRowWidthEqually(widthMode)) {
            equalRowShares(widthMeasureSpec, heightMeasureSpec)
        } else {
            null
        }

        var matchHeight = false
        var skippedMeasure = false
        // What the deferred ratio children will take on our axis, accumulated as we skip past them.
        var idealRatioLength = 0

        // What the children that can't give have taken, which is what the rest have to share.
        var fixedLength = 0

        // As in `measureVertical`: what the children that can bend are allowed when a percent
        // sibling wants the same room. A row that divides equally never has one, so the two never
        // meet.
        var percentShares: Shares? = null

        // See how wide everyone is. Also remember max height.
        //
        // As in `measureVertical`: the children that can give are measured after the ones that
        // can't, and all against the same room, so a photo beside a caption divides what is left
        // with its siblings rather than taking the row and leaving them measured against nothing.
        for (pass in 0..1) {
            if (pass == 1) {
                fixedLength = totalLength
                if (percentTotal > 0f) {
                    percentShares = sharesWithPercentChildren(
                        vertical = false,
                        fixedLength = fixedLength,
                        mainMeasureSpec = widthMeasureSpec,
                        crossMeasureSpec = heightMeasureSpec
                    )
                }
            }

        for (i in 0..<count) {
            val child = getChildAt(i) ?: continue

            if (child.visibility == GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams

            if (child.givesOnShrink(horizontal = true) != (pass == 1)) continue

            // Any width-auto + ratio in a bounded layout: defer to a third pass so ratio items
            // never overflow the space remaining after siblings are measured. Their length still
            // counts toward `S` below — it's derived from our height, which is settled before the
            // width solve runs, so a ratio child is fixed content as far as the solve is concerned.
            if (lp.aspectRatio > 0f
                && lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxWidthPercent == 0f
                && (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST)) {
                ratioFillChildren.add(child)
                idealRatioLength +=
                    ratioMainExtent(lp, vertical = false, crossSpecSize = heightSize) +
                            lp.marginStart + lp.marginEnd
                allFillParent = false
                continue
            }

            if (lp.maxWidthPercent > 0) {
                childrenWithMaxPercent.add(child)
            }

            if (lp.width == 0 && lp.maxWidthPercent > 0) {
                // Don't measure percent children yet, and don't let them into the sum: a percent
                // child is a fraction of a length it also contributes to, so including it here
                // would make the percentage describe something other than the finished stack.
                // Only its margins count — they're fixed content. What's left in totalLength is
                // `S` for the `W = S / (1 - P)` solve below, and these children get sized in the
                // distribution pass once there's a length to resolve against.
                val totalLength = this.totalLength
                this.totalLength =
                    max(totalLength, totalLength + lp.marginStart + lp.marginEnd)
                skippedMeasure = true
            } else {
                val childVerticalMargins = lp.topMargin + lp.bottomMargin
                var oldHeight = Int.MIN_VALUE
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    oldHeight = 0
                    lp.height = if (heightMode == MeasureSpec.EXACTLY) {
                        // Our height is settled; resolve against it directly.
                        ((heightSize * lp.maxHeightPercent).toInt() - childVerticalMargins)
                            .coerceAtLeast(0)
                    } else {
                        // We're sized by our content, and a child that's a fraction of our height
                        // can't be part of what decides it. Measure it at its own content for now
                        // and give it its share once our height is known.
                        //
                        // This also avoids resolving against `heightSize`, which is 0 under an
                        // UNSPECIFIED spec: the old arithmetic went negative and aliased into
                        // MATCH_PARENT or WRAP_CONTENT depending on how many pixels of margin the
                        // child happened to have.
                        crossAxisPercentChildren.add(child)
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }

                // Width to withhold from the child, so it measures against:
                // (row width - padding - margins - width used)
                val share = shares?.get(i) ?: percentShares?.perChild?.get(i) ?: -1
                val widthUsed = when {
                    // withhold all but the share, pinning the child to exactly that width
                    // (an equal one, or one a percent sibling's claim is already out of)
                    share >= 0 ->
                        (MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
                                - lp.marginStart - lp.marginEnd - share).coerceAtLeast(0)
                    // withhold what the children that can't give took, so the ones that can are
                    // all measured against the same room
                    percentTotal == 0f -> if (pass == 1) fixedLength else totalLength
                    // withhold nothing. totalLength keeps moving until percent children are
                    // sized in the distribution pass below, so using it here would measure
                    // against a stale value.
                    else -> 0
                }

                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    widthUsed,
                    heightMeasureSpec,
                    childVerticalMargins
                )

                if (oldHeight != Int.MIN_VALUE) {
                    lp.height = oldHeight
                }

                if (lp.aspectRatio > 0f) {
                    remeasureWithAspectRatio(child, lp, widthMeasureSpec, heightMeasureSpec)
                }

                val childWidth = child.measuredWidth
                val totalLength = this.totalLength
                this.totalLength = max(
                    totalLength, totalLength + childWidth + lp.marginStart + lp.marginEnd
                )
            }

            var matchHeightLocally = false
            if (heightMode != MeasureSpec.EXACTLY && lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                // The height of the linear layout will scale, and at least one child said it wanted to match our
                // height. Set a flag indicating that we need to remeasure at least that view when we know our height.
                matchHeight = true
                matchHeightLocally = true
            }

            val margin = lp.topMargin + lp.bottomMargin
            val measuredHeight = child.measuredHeight + margin
            childState = combineMeasuredStates(childState, child.measuredState)

            // A child that's a fraction of our height doesn't get a say in what our height is —
            // otherwise the percentage would end up describing a length it set itself. Unless it is
            // all we have: with no basis the percentage is never taken, so what it measured is its
            // content and counts like anyone else's.
            if (child !in crossAxisPercentChildren || !crossAxisHasBasis) {
                maxHeight = max(maxHeight, measuredHeight)

                if (lp.maxWidthPercent > 0) {
                    // Heights of max percentage Views are bogus if we end up remeasuring, so keep them separate.
                    percentMaxHeight =
                        max(percentMaxHeight, if (matchHeightLocally) margin else measuredHeight)
                } else {
                    alternativeMaxHeight =
                        max(alternativeMaxHeight, if (matchHeightLocally) margin else measuredHeight)
                }
            }

            allFillParent = allFillParent && lp.height == ViewGroup.LayoutParams.MATCH_PARENT
        }
        }

        // Add in our padding
        totalLength += paddingStart + paddingEnd

        // `S` — the fixed content: everything measured above plus what the deferred ratio children
        // will take, less the percent children's margins, which the distribution pass takes out of
        // their own slots rather than up front.
        val percentMargins = childrenWithMaxPercent.sumOf {
            val lp = it.layoutParams as LayoutParams
            lp.marginStart + lp.marginEnd
        }

        // Check against our minimum width
        val solvedWidth = solveMainAxisLength(
            mode = widthMode,
            specSize = MeasureSpec.getSize(widthMeasureSpec),
            fixedLength = totalLength - percentMargins + idealRatioLength,
            percentTotal = percentTotal,
            borrowed = if (percentTotal > 0f) borrowedPercentBase(horizontal = true) else 0,
            autoAncestor = percentTotal >= 1f && hasAutoSizedAncestor(horizontal = true),
            // Only asked when we're about to solve — it walks the subtree.
            hasLengthBasis = percentTotal <= 0f || establishesLength(horizontal = true)
        )
        var width = solvedWidth ?: totalLength

        // Whether there's a length for percent children to be a fraction of. An EXACTLY spec is one
        // without needing a solve; otherwise it's whatever we solved for, and nothing means the
        // percent children measure at their content instead.
        val hasResolvedWidth = widthMode == MeasureSpec.EXACTLY || solvedWidth != null
        width = max(width, suggestedMinimumWidth)

        // Reconcile our calculated size with the widthMeasureSpec
        var widthSizeAndState = resolveSizeAndState(width, widthMeasureSpec, 0)
        width = widthSizeAndState and MEASURED_SIZE_MASK

        // Either expand children with percentage dimensions to take up available space or shrink them if they extend
        // beyond our current bounds.
        // Unbounded: `width` is a base to resolve against, not a length to divide up, so hand the
        // distribution exactly what the percent children add up to and let them overflow us.
        var delta = if (widthMode == MeasureSpec.UNSPECIFIED && solvedWidth != null) {
            (width * percentTotal).roundToInt() - percentMargins
        } else {
            width - totalLength
        }

        // Whether a child's width below is an allowance we handed it rather than something it
        // measured. The cross-axis pass re-measures widths, and must not hand back the space the
        // overflow branch just took away.
        var widthsWereRationed = delta < 0

        // If the delta is negative, the content is too big for the layout. We need to find
        // children with wrap_content width that we can shrink.
        if (delta < 0) {
            val shrinkableChildren = mutableListOf<View>()
            val originalWidths = mutableListOf<Int>()
            var totalShrinkableWidth = 0

            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE) {
                    continue
                }

                if (child.givesOnShrink(horizontal = true)) {
                    shrinkableChildren.add(child)

                    val childWidth = child.measuredWidth
                    originalWidths.add(childWidth)
                    totalShrinkableWidth += childWidth
                }
            }

            if (!shrinkableChildren.isEmpty() && totalShrinkableWidth >= abs(delta)) {
                fun heightSpecFor(child: View): Int {
                    val lp = child.layoutParams as LayoutParams
                    return when {
                        lp.height == 0 && lp.maxHeightPercent > 0 ->
                            if (heightMode == MeasureSpec.EXACTLY) {
                                val childHeight =
                                    ((heightSize * lp.maxHeightPercent).toInt() - lp.topMargin - lp.bottomMargin)
                                        .coerceAtLeast(0)
                                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                            } else {
                                // Our height isn't settled, so keep what this measured; the
                                // cross-axis pass gives it its share once we know what we ended up.
                                MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
                            }

                        lp.height == ViewGroup.LayoutParams.MATCH_PARENT && heightMode != MeasureSpec.EXACTLY ->
                            MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)

                        else -> getChildMeasureSpec(
                            heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height
                        )
                    }
                }

                // As in `measureVertical`: each gives in proportion to what it asked for, and
                // `AT_MOST` lets it answer with what it can actually be.
                var owed = -delta
                var giving: List<View> = shrinkableChildren
                while (owed > 0 && giving.isNotEmpty()) {
                    val asked = giving.sumOf { it.measuredWidth }
                    if (asked <= 0) break

                    val shrinkRatio = ((asked - owed).toFloat() / asked).coerceAtLeast(0f)
                    val stillGiving = mutableListOf<View>()
                    for (child in giving) {
                        val was = child.measuredWidth
                        val share = max(0, (was * shrinkRatio).toInt())

                        child.measure(
                            MeasureSpec.makeMeasureSpec(share, MeasureSpec.AT_MOST),
                            heightSpecFor(child)
                        )

                        owed -= was - child.measuredWidth
                        if (child.measuredWidth <= share) stillGiving.add(child)
                    }

                    // Nobody had anything more to give, or everyone gave their share.
                    if (stillGiving.size == giving.size) break
                    giving = stillGiving
                }

                // Recalculate totalLength, and our height with it: a child that gave something
                // back may be shorter for it, and our height was taken from what it asked for.
                totalLength = 0
                maxHeight = 0
                percentMaxHeight = 0
                alternativeMaxHeight = 0
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) {
                        continue
                    }

                    val lp = child.layoutParams as LayoutParams
                    totalLength += child.measuredWidth + lp.marginStart + lp.marginEnd

                    val margin = lp.topMargin + lp.bottomMargin
                    val matchHeightLocally = heightMode != MeasureSpec.EXACTLY &&
                            lp.height == ViewGroup.LayoutParams.MATCH_PARENT
                    val childHeight = if (matchHeightLocally) margin else child.measuredHeight + margin

                    if (child !in crossAxisPercentChildren || !crossAxisHasBasis) {
                        maxHeight = max(maxHeight, child.measuredHeight + margin)

                        if (lp.maxWidthPercent > 0) {
                            percentMaxHeight = max(percentMaxHeight, childHeight)
                        } else {
                            alternativeMaxHeight = max(alternativeMaxHeight, childHeight)
                        }
                    }
                }
                totalLength += paddingStart + paddingEnd

                // Delta should now be close to zero
                delta = width - totalLength
            } else {
                // Fixed children overflow the parent, and WRAP_CONTENT children can't absorb it;
                // zero out all WRAP_CONTENT children to match iOS failure behavior.
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) continue
                    val lp = child.layoutParams as LayoutParams
                    if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxWidthPercent == 0f) {
                        val heightSpec = getChildMeasureSpec(
                            heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height
                        )
                        child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), heightSpec)
                    }
                }
                totalLength = 0
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) continue
                    val lp = child.layoutParams as LayoutParams
                    totalLength += child.measuredWidth + lp.marginStart + lp.marginEnd
                }
                totalLength += paddingStart + paddingEnd
                delta = width - totalLength
            }
        }

        // Measure any skipped children or distribute leftover space to percentage-width children.
        if (skippedMeasure || (delta != 0 && !childrenWithMaxPercent.isEmpty())) {
            Collections.sort<View>(childrenWithMaxPercent, Comparator { v1: View, v2: View ->
                val p1 = (v1.layoutParams as LayoutParams).maxWidthPercent
                val p2 = (v2.layoutParams as LayoutParams).maxWidthPercent
                p1.compareTo(p2)
            })

            val maxPercentCount = childrenWithMaxPercent.size

            // Distribute equal slots across percent children (iOS HStack/VStack-style fair
            // share), then subtract each child's own margin from its slot to get content
            // width. A margined child ends up narrower than its unmargined siblings, instead
            // of every percent child getting equal content while margins pile on top.
            val percentMargins = childrenWithMaxPercent.sumOf {
                val marginLp = it.layoutParams as LayoutParams
                marginLp.marginStart + marginLp.marginEnd
            }
            // As in `measureVertical`: less what the ratio children are owed, since they are
            // measured after this against what is left.
            var slotRemaining =
                (delta + percentMargins - (percentShares?.ratioReserve ?: 0)).coerceAtLeast(0)

            val lastChildIndex = maxPercentCount - 1
            for (i in 0..<maxPercentCount) {
                val child = childrenWithMaxPercent[i]
                val lp = child.layoutParams as LayoutParams

                val heightSpec: Int
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    heightSpec = if (heightMode == MeasureSpec.EXACTLY) {
                        // Our height is settled; resolve against it directly.
                        val childHeight =
                            ((heightSize * lp.maxHeightPercent).toInt() - lp.topMargin - lp.bottomMargin)
                                .coerceAtLeast(0)
                        MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                    } else {
                        // The same bargain the first pass makes. `heightSize` is a ceiling we may not
                        // fill, so measure at content now and hand out the share once our height is
                        // known. Without this a child that's a percent on both axes resolved its
                        // height against the ceiling while one that's a percent on the cross axis
                        // alone resolved against our height — two answers for one declaration.
                        crossAxisPercentChildren.add(child)
                        getChildMeasureSpec(
                            heightMeasureSpec,
                            paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                } else {
                    heightSpec = getChildMeasureSpec(
                        heightMeasureSpec,
                        paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                        lp.height
                    )
                }

                // An unbounded spec is fine to distribute against as long as we solved a base for
                // it — a borrowed viewport, or `S / (1 - P)`. Without one there's nothing to take a
                // percentage of, so fall back to the child's own content rather than leaving it
                // unmeasured, matching iOS, where a percent with no parent size behaves as auto.
                if (hasResolvedWidth) {
                    val remaining = maxPercentCount - i

                    val actualPercent: Float = when {
                        slotRemaining >= (width * lp.maxWidthPercent) * remaining ->
                            lp.maxWidthPercent
                        width > 0 ->
                            slotRemaining.toFloat() / remaining.toFloat() / width.toFloat()
                        // width == 0: slot collapses to 0 below regardless, avoid NaN.
                        else -> 0f
                    }

                    // When delta < 0 siblings already overflow the parent; give each percent
                    // child 0px to match iOS failure behavior (fixed children win).
                    var slot = if (delta >= 0) {
                        (actualPercent * width).toInt()
                    } else {
                        0
                    }
                    if (i == lastChildIndex && delta >= 0) {
                        slot = min(slot, slotRemaining)
                    }

                    slotRemaining -= slot

                    val childMargin = lp.marginStart + lp.marginEnd
                    val childWidth = (slot - childMargin).coerceAtLeast(0)

                    val widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                    child.measure(widthSpec, heightSpec)

                    if (lp.aspectRatio > 0f) {
                        remeasureWithAspectRatio(child, lp, widthMeasureSpec, heightMeasureSpec)
                    }

                    // Child may now not fit in horizontal dimension.
                    childState = combineMeasuredStates(
                        childState, child.measuredState and MEASURED_STATE_MASK
                    )
                } else {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        heightSpec
                    )
                    childState = combineMeasuredStates(childState, child.measuredState)
                }
            }

            // Determine height now that all views have been measured. Rebuild the length from
            // scratch: it still holds the fixed content from the first pass, and every child is
            // about to be counted again — including the ones already in there.
            totalLength = 0
            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE) {
                    continue
                }

                val lp = child.layoutParams as LayoutParams
                val margin = lp.topMargin + lp.bottomMargin
                val measuredHeight = child.measuredHeight + margin

                // Same rule as the first pass: a child that's a fraction of our height doesn't get a
                // say in what our height is. Leaving it out there but counting it here made our
                // height depend on whether some *other* child happened to have a main-axis percent,
                // since that's the only thing that brings us into this loop.
                if (child !in crossAxisPercentChildren || !crossAxisHasBasis) {
                    maxHeight = max(maxHeight, measuredHeight)

                    val matchHeightLocally = heightMode != MeasureSpec.EXACTLY &&
                            lp.height == ViewGroup.LayoutParams.MATCH_PARENT

                    alternativeMaxHeight =
                        max(alternativeMaxHeight, if (matchHeightLocally) margin else measuredHeight)
                }

                allFillParent = allFillParent && lp.height == ViewGroup.LayoutParams.MATCH_PARENT

                val totalLength = this.totalLength
                this.totalLength = max(
                    totalLength,
                    totalLength + child.measuredWidth + lp.marginStart + lp.marginEnd
                )
            }

            totalLength += paddingStart + paddingEnd
        } else {
            alternativeMaxHeight = max(alternativeMaxHeight, percentMaxHeight)
        }

        // Third pass: measure deferred ratio children using the remaining available width so they
        // never push siblings off screen.
        if (ratioFillChildren.isNotEmpty()) {
            val boundedParentW = if (widthMode == MeasureSpec.AT_MOST)
                MeasureSpec.getSize(widthMeasureSpec) else width

            // What everyone else ended up taking, percent slots included. The first-pass total
            // stood in for this before, which counted the percent children's margins but not the
            // slots they were given, so the ratio children were offered space already spoken for.
            var consumed = paddingStart + paddingEnd
            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE || child in ratioFillChildren) continue
                val lp = child.layoutParams as LayoutParams
                consumed += child.measuredWidth + lp.marginStart + lp.marginEnd
            }
            val remaining = (boundedParentW - consumed).coerceAtLeast(0)

            val idealHs = IntArray(ratioFillChildren.size)
            val idealWs = IntArray(ratioFillChildren.size)
            for (i in ratioFillChildren.indices) {
                val lp = ratioFillChildren[i].layoutParams as LayoutParams
                idealHs[i] = ratioCrossExtent(lp, vertical = false, crossSpecSize = heightSize)
                idealWs[i] = (idealHs[i] * lp.aspectRatio).toInt()
            }

            val childWidths = IntArray(ratioFillChildren.size)
            val order = ratioFillChildren.indices.sortedBy { idealWs[it] }
            var widthLeft = remaining
            var slotsLeft = ratioFillChildren.size
            for (i in order) {
                val lp = ratioFillChildren[i].layoutParams as LayoutParams
                val mainMargin = lp.marginStart + lp.marginEnd
                val slot = (widthLeft / slotsLeft - mainMargin).coerceAtLeast(0)
                val childW = min(idealWs[i], slot)
                childWidths[i] = childW
                widthLeft -= (childW + mainMargin)
                slotsLeft -= 1
            }

            for (i in ratioFillChildren.indices) {
                val child = ratioFillChildren[i]
                val lp = child.layoutParams as LayoutParams

                val childW = childWidths[i]
                val childH = min(idealHs[i], (childW / lp.aspectRatio).toInt()).coerceAtLeast(0)

                child.measure(
                    MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
                )
                childState = combineMeasuredStates(childState, child.measuredState)

                val measuredHeight = child.measuredHeight + lp.topMargin + lp.bottomMargin
                maxHeight = max(maxHeight, measuredHeight)
                alternativeMaxHeight = max(alternativeMaxHeight, measuredHeight)
            }
        }

        if (!allFillParent && heightMode != MeasureSpec.EXACTLY) {
            maxHeight = alternativeMaxHeight
        }

        // Check against our minimum height
        maxHeight += paddingTop + paddingBottom
        maxHeight = max(maxHeight, suggestedMinimumHeight)

        val heightSizeAndState = resolveSizeAndState(maxHeight, heightMeasureSpec, childState)

        // Now that our height is settled, give the deferred cross-axis percent children their share
        // of it. A child that's auto on the main axis is measured afresh rather than pinned to what
        // it measured while tall. A child holding a distributed slot keeps it — that width is its
        // share, not a measurement — and so does everyone if the overflow branch already rationed
        // the space.
        // Skipped when there is no basis and nothing takes the whole: our height came from these
        // children, so handing them a share of it is handing them a share of themselves. A child at
        // the whole is the exception, as in `measureVertical`.
        if (crossAxisPercentChildren.isNotEmpty() && (crossAxisHasBasis || crossAxisSharesTheWhole)) {
            val available = ((heightSizeAndState and MEASURED_SIZE_MASK) - paddingTop - paddingBottom)
                .coerceAtLeast(0)
            for (child in crossAxisPercentChildren) {
                val lp = child.layoutParams as LayoutParams
                val margins = lp.topMargin + lp.bottomMargin
                val childHeight = (minOf((available * lp.maxHeightPercent).toInt(), available) - margins)
                    .coerceAtLeast(0)

                val hasSlot = lp.width == 0 && lp.maxWidthPercent > 0
                val widthSpec = if (hasSlot || widthsWereRationed) {
                    MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY)
                } else {
                    getChildMeasureSpec(
                        widthMeasureSpec,
                        paddingStart + paddingEnd + lp.marginStart + lp.marginEnd,
                        lp.width
                    )
                }

                child.measure(
                    widthSpec, MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                )
                childState = combineMeasuredStates(childState, child.measuredState)
            }
        }

        // Every child has a final width now — distributed slots, ratio columns, and the cross-axis
        // children just re-measured. Rebuild the length from all of them so what we report and what
        // `onLayout` positions against agree. The ratio pass used to report the first-pass total
        // plus the ratio columns, which left the percent slots out entirely.
        if (percentTotal > 0f || ratioFillChildren.isNotEmpty() ||
            crossAxisPercentChildren.isNotEmpty()
        ) {
            totalLength = paddingStart + paddingEnd
            for (i in 0..<count) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE) continue
                val lp = child.layoutParams as LayoutParams
                totalLength += child.measuredWidth + lp.marginStart + lp.marginEnd
            }

            // Unless the spec fixed our width, report the content — in particular, when the base was
            // a borrowed viewport we may well be wider than it, which is the whole point of being
            // inside a scroll.
            if (widthMode != MeasureSpec.EXACTLY) {
                widthSizeAndState = resolveSizeAndState(totalLength, widthMeasureSpec, childState)
            }
        }

        setMeasuredDimension(widthSizeAndState, heightSizeAndState)

        if (matchHeight) {
            forceUniformHeight(count, widthMeasureSpec)
        }
    }

    /**
     * Whether this row divides its width into equal shares rather than paying children in the order
     * they were declared.
     *
     * Declaration order works only while there's width to go round. Once there isn't, whichever
     * child happens to be first takes all it wants and a later one can be offered nothing at all: a
     * CTA beside a wrapping paragraph disappeared outright, and an image beside one measured at zero
     * and drew nothing. The shrink tiers never catch it, because paying in order always sums to
     * exactly the row and so never produces the shortfall they look for.
     */
    private fun sharesRowWidthEqually(widthMode: Int): Boolean {
        // Nothing to divide: an unbounded row is as wide as its children turn out to be.
        if (widthMode == MeasureSpec.UNSPECIFIED) return false

        var sharers = 0
        for (i in 0..<childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue

            // Only an all-auto row, as on web. A child with a length of its own is a demand rather
            // than a share, and ratio children take their width from their height in a later pass.
            val lp = child.layoutParams as? LayoutParams ?: return false
            if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) return false
            if (lp.maxWidthPercent != 0f) return false
            if (lp.aspectRatio > 0f) return false

            sharers++
        }

        // One child's share is the whole row, which is what it would have been offered anyway.
        return sharers > 1
    }

    /**
     * What each child may take of the row, by index: an equal share, capped at its content, with
     * whatever a child doesn't need passed on to the rest. `GONE` indices are left at zero.
     *
     * Greedy fair-share, smallest content first — the same rule the ratio passes use. A child that
     * fits inside its share settles at its content and hands the surplus back, so only the ones that
     * can't fit divide what's left: a CTA keeps its whole line while the paragraph beside it wraps,
     * and an over-long CTA truncates inside its share instead of taking the row.
     */
    private fun equalRowShares(widthMeasureSpec: Int, heightMeasureSpec: Int): IntArray {
        val count = childCount
        // -1 marks a child that isn't sharing, to tell it from one whose content is genuinely zero.
        val content = IntArray(count) { -1 }

        // What each child would take with nothing holding it back. Measured against an unbounded
        // width, which is what asks a label for its whole line rather than for a wrapped one.
        for (i in 0..<count) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue
            val lp = child.layoutParams as LayoutParams

            child.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                getChildMeasureSpec(
                    heightMeasureSpec,
                    paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                    // A percent height has nothing to resolve against yet, and a child's whole-line
                    // width doesn't depend on the height it's offered. Ask for its content.
                    if (lp.height == 0 && lp.maxHeightPercent > 0) {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    } else {
                        lp.height
                    }
                )
            )
            content[i] = child.measuredWidth
        }

        val sharing = (0..<count).filter { content[it] >= 0 }
        val shares = IntArray(count)
        var widthLeft =
            (MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd).coerceAtLeast(0)
        var slotsLeft = sharing.size

        for (i in sharing.sortedBy { content[it] }) {
            val lp = getChildAt(i).layoutParams as LayoutParams
            val margins = lp.marginStart + lp.marginEnd
            val slot = (widthLeft / slotsLeft - margins).coerceAtLeast(0)
            shares[i] = min(content[i], slot)
            widthLeft -= (shares[i] + margins)
            slotsLeft -= 1
        }

        return shares
    }

    /**
     * What the children that can bend are allowed on the stack axis when a percent sibling wants
     * the same room: a cap for each child that can give, and the total the deferred ratio children
     * are to be left.
     */
    private class Shares(val perChild: IntArray, val ratioReserve: Int)

    /**
     * How a stack divides itself between the children that can bend to it. Null when nothing has
     * to give; `-1` in [Shares.perChild] for a child that isn't sharing.
     *
     * A percent child is sized from what its siblings left, so it is paid last: a child that can
     * give is asked for its content first, and a ratio child isn't measured until later still.
     * Whoever goes first takes what it likes, and at 100% the percent child leaves nothing for
     * anyone. Every claim is a demand like any other, so they all join the same greedy fair share
     * the ratio passes use — smallest demand first, each capped at its share of what is left. A
     * full-length box beside an auto image, or beside a square, ends up with half the stack, as on
     * iOS. A ratio child bends with the rest: cut on the stack axis, it takes the other axis with
     * it rather than holding a length the percentage was supposed to divide with it.
     *
     * @param fixedLength what the children that can't give have taken, margins included.
     * @param mainMeasureSpec the spec for the stack axis.
     * @param crossMeasureSpec the spec for the other axis.
     * @return the shares, or null if the stack has room for every demand.
     */
    private fun sharesWithPercentChildren(
        vertical: Boolean,
        fixedLength: Int,
        mainMeasureSpec: Int,
        crossMeasureSpec: Int
    ): Shares? {
        val mode = MeasureSpec.getMode(mainMeasureSpec)
        // An unbounded stack is as long as its children turn out to be, and with no basis a percent
        // child stands on its content: either way there's nothing being divided.
        if (mode == MeasureSpec.UNSPECIFIED) return null
        if (mode != MeasureSpec.EXACTLY && !establishesLength(horizontal = !vertical)) return null

        val count = childCount
        val base = MeasureSpec.getSize(mainMeasureSpec)
        val crossSize = MeasureSpec.getSize(crossMeasureSpec)

        // Demand to child index, with -1 for a percent child: its claim competes, but the
        // distribution pass is what hands it out.
        val queue = mutableListOf<Pair<Int, Int>>()
        val margins = IntArray(count)
        val isRatio = BooleanArray(count)
        var percentMargins = 0
        var bending = 0

        for (i in 0..<count) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue

            val lp = child.layoutParams as LayoutParams
            margins[i] =
                if (vertical) lp.topMargin + lp.bottomMargin else lp.marginStart + lp.marginEnd
            val declared = if (vertical) lp.height else lp.width
            val percent = if (vertical) lp.maxHeightPercent else lp.maxWidthPercent

            when {
                declared == 0 && percent > 0f -> {
                    queue.add((base * percent).toInt().coerceAtLeast(0) to -1)
                    percentMargins += margins[i]
                }

                // The same child the deferred ratio pass will take: its length comes from the
                // other axis, which is settled, so what it wants is known without measuring it.
                lp.aspectRatio > 0f && declared == ViewGroup.LayoutParams.WRAP_CONTENT
                        && percent == 0f -> {
                    isRatio[i] = true
                    queue.add(ratioMainExtent(lp, vertical, crossSize) + margins[i] to i)
                    bending++
                }

                child.givesOnShrink(horizontal = !vertical) -> {
                    queue.add(contentLength(child, vertical, crossMeasureSpec) + margins[i] to i)
                    bending++
                }
            }
        }

        // Nobody to take it from, or nobody asking them to.
        if (bending == 0 || bending == queue.size) return null

        val padding = if (vertical) paddingTop + paddingBottom else paddingStart + paddingEnd
        // A percent child's margins come out of its own slot, here as in the distribution pass, so
        // they're part of what's being divided rather than fixed content.
        var room = base - padding - (fixedLength - percentMargins)

        // Room for every demand, so nothing has to give and everyone is measured as they asked.
        if (room <= 0 || queue.sumOf { it.first } <= room) return null

        val shares = IntArray(count) { -1 }
        var ratioReserve = 0
        var slotsLeft = queue.size
        // Smallest demand first, so a child that fits inside its share settles at its content and
        // hands the surplus to the rest.
        for ((demand, index) in queue.sortedBy { it.first }) {
            val take = min(demand, room / slotsLeft)
            if (index >= 0) {
                // A ratio child is measured by its own pass, off what the percent children leave,
                // so its share is held back rather than handed over.
                if (isRatio[index]) ratioReserve += take
                else shares[index] = (take - margins[index]).coerceAtLeast(0)
            }
            room -= take
            slotsLeft -= 1
        }

        return Shares(shares, ratioReserve)
    }

    /** What [child] takes on the stack axis with nothing holding it back. */
    private fun contentLength(child: View, vertical: Boolean, crossMeasureSpec: Int): Int {
        val lp = child.layoutParams as LayoutParams
        val crossMargins =
            if (vertical) lp.marginStart + lp.marginEnd else lp.topMargin + lp.bottomMargin
        val crossPadding =
            if (vertical) paddingStart + paddingEnd else paddingTop + paddingBottom
        val declared = if (vertical) lp.width else lp.height
        val percent = if (vertical) lp.maxWidthPercent else lp.maxHeightPercent
        val isCrossPercent = declared == 0 && percent > 0f

        val crossMode = MeasureSpec.getMode(crossMeasureSpec)
        val crossSpec = if (isCrossPercent && crossMode == MeasureSpec.EXACTLY) {
            // Our cross length is settled; resolve against it directly.
            val size = ((MeasureSpec.getSize(crossMeasureSpec) * percent).toInt() - crossMargins)
                .coerceAtLeast(0)
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        } else {
            // Unsettled, a percent child stands on its content until the cross-axis pass gives it
            // its share — the same bargain the measure loop makes.
            getChildMeasureSpec(
                crossMeasureSpec,
                crossPadding + crossMargins,
                if (isCrossPercent) ViewGroup.LayoutParams.WRAP_CONTENT else declared
            )
        }

        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        if (vertical) child.measure(crossSpec, unbounded) else child.measure(unbounded, crossSpec)
        return if (vertical) child.measuredHeight else child.measuredWidth
    }

    private fun forceUniformHeight(count: Int, widthMeasureSpec: Int) {
        // Pretend that the linear layout has an exact size. This is the measured height of
        // ourselves. The measured height should be the max height of the children, changed
        // to accommodate the heightMeasureSpec from the parent
        val uniformMeasureSpec =
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        for (i in 0..<count) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as LayoutParams

                if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    // Temporarily force children to reuse their old measured width
                    val oldWidth = lp.width
                    lp.width = child.measuredWidth

                    // Remeasure with new dimensions
                    measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0)
                    lp.width = oldWidth
                }
            }
        }
    }

    /**
     * Position the children during a layout pass if the orientation is set to [.VERTICAL].
     *
     * @see .getOrientation
     * @see .setOrientation
     * @see .onLayout
     */
    private fun layoutVertical(left: Int, top: Int, right: Int, bottom: Int) {
        val paddingLeft = this.paddingLeft

        var childTop: Int
        var childLeft: Int

        // Where right end of child should go
        val width = right - left
        val childRight = width - paddingRight

        // Space available for child
        val childSpace = width - paddingLeft - paddingRight

        val count = childCount

        val majorGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val minorGravity = gravity and GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK

        childTop = when (majorGravity) {
            // mTotalLength contains the padding already
            Gravity.BOTTOM -> paddingTop + bottom - top - totalLength
            Gravity.CENTER_VERTICAL -> paddingTop + (bottom - top - totalLength) / 2
            Gravity.TOP -> paddingTop
            else -> paddingTop
        }

        for (i in 0..<count) {
            val child = getChildAt(i)
            if (child.visibility == GONE) {
                continue
            }

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            val lp = child.layoutParams as LayoutParams

            var gravity = lp.gravity
            if (gravity < 0) {
                gravity = minorGravity
            }
            val absoluteGravity = GravityCompat.getAbsoluteGravity(gravity, layoutDirection)
            childLeft = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.CENTER_HORIZONTAL -> paddingLeft + ((childSpace - childWidth - lp.leftMargin - lp.rightMargin) / 2) + lp.leftMargin
                Gravity.RIGHT -> childRight - childWidth - lp.rightMargin
                Gravity.LEFT -> paddingLeft + lp.leftMargin
                else -> paddingLeft + lp.leftMargin
            }

            childTop += lp.topMargin
            setChildFrame(child, childLeft, childTop, childWidth, childHeight)
            childTop += childHeight + lp.bottomMargin
        }
    }

    /**
     * Position the children during a layout pass if the orientation is set to [.HORIZONTAL].
     *
     * @see .getOrientation
     * @see .setOrientation
     * @see .onLayout
     */
    private fun layoutHorizontal(left: Int, top: Int, right: Int, bottom: Int) {
        val paddingTop = getPaddingTop()

        var childTop: Int
        var childLeft: Int

        // Where bottom of child should go
        val height = bottom - top
        val childBottom = height - paddingBottom

        // Space available for child
        val childSpace = height - paddingTop - paddingBottom

        val count = childCount

        val majorGravity = gravity and GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK
        val minorGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK

        childLeft = when (GravityCompat.getAbsoluteGravity(majorGravity, layoutDirection)) {
            // mTotalLength contains the padding already
            Gravity.RIGHT -> paddingLeft + right - left - totalLength
            // mTotalLength contains the padding already
            Gravity.CENTER_HORIZONTAL -> paddingLeft + (right - left - totalLength) / 2
            Gravity.LEFT -> paddingLeft
            else -> paddingLeft
        }

        var start = 0
        var dir = 1
        //In case of RTL, start drawing from the last child.
        if (isLayoutRtl) {
            start = count - 1
            dir = -1
        }

        for (i in 0..<count) {
            val childIndex = start + dir * i
            val child = getChildAt(childIndex)

            if (child.visibility == GONE) {
                continue
            }

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            val lp = child.layoutParams as LayoutParams

            var gravity = lp.gravity
            if (gravity < 0) {
                gravity = minorGravity
            }

            childTop = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.TOP -> paddingTop + lp.topMargin
                Gravity.CENTER_VERTICAL -> paddingTop + ((childSpace - childHeight - lp.topMargin - lp.bottomMargin) / 2) + lp.topMargin
                Gravity.BOTTOM -> childBottom - childHeight - lp.bottomMargin
                else -> paddingTop
            }

            childLeft += lp.leftMargin
            setChildFrame(child, childLeft, childTop, childWidth, childHeight)
            childLeft += childWidth + lp.rightMargin
        }
    }

    private fun setChildFrame(child: View, left: Int, top: Int, width: Int, height: Int) {
        child.layout(left, top, left + width, top + height)
    }

    /**
     * Per-child layout information associated with WeightlessLinearLayout.
     */
    internal class LayoutParams : MarginLayoutParams {

        var maxWidthPercent: kotlin.Float = 0f
        var maxHeightPercent: kotlin.Float = 0f
        var aspectRatio: kotlin.Float = 0f

        /**
         * Gravity for the view associated with these LayoutParams.
         *
         * @see Gravity
         */
        var gravity: Int = -1

        /**
         * {@inheritDoc}
         */
        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            val a = c.obtainStyledAttributes(attrs, R.styleable.WeightlessLinearLayout_Layout)

            maxWidthPercent =
                a.getFloat(R.styleable.WeightlessLinearLayout_Layout_maxPercentWidth, 0f)
            maxHeightPercent =
                a.getFloat(R.styleable.WeightlessLinearLayout_Layout_maxPercentHeight, 0f)
            gravity = a.getInt(R.styleable.WeightlessLinearLayout_Layout_android_layout_gravity, -1)

            a.recycle()
        }

        /**
         * {@inheritDoc}
         */
        constructor(width: Int, height: Int) : super(width, height)

        /**
         * Creates a new set of layout parameters with the specified width, height and max width/height percentages.
         *
         * @param width the width, either [.MATCH_PARENT], [.WRAP_CONTENT] or a fixed size in pixels
         * @param height the height, either [.MATCH_PARENT], [.WRAP_CONTENT] or a fixed size in pixels
         */
        constructor(
            width: Int, height: Int, maxWidthPercent: kotlin.Float, maxHeightPercent: kotlin.Float,
            aspectRatio: kotlin.Float = 0f
        ) : super(width, height) {
            this.maxWidthPercent = maxWidthPercent
            this.maxHeightPercent = maxHeightPercent
            this.aspectRatio = aspectRatio
        }

        /**
         * {@inheritDoc}
         */
        constructor(p: ViewGroup.LayoutParams?) : super(p)

        /**
         * {@inheritDoc}
         */
        constructor(source: MarginLayoutParams?) : super(source)

        override fun toString(): String {
            return String.format(
                "LayoutParams{ width = %d, height = %d, maxWidth = %.2f, maxHeight = %.2f, aspectRatio = %.3f }",
                width,
                height,
                maxWidthPercent,
                maxHeightPercent,
                aspectRatio
            )
        }
    }

    /**
     * Re-measures [child] to enforce [LayoutParams.aspectRatio] after initial sizing.
     *
     * - One dimension auto: derives the auto dimension from the measured fixed/percent dimension.
     * - Both auto: fills the primary axis of this layout's orientation and derives the other.
     * - Both fixed: no-op per spec (explicit values win).
     */
    /** The declared percentages on the stack axis, summed (0.5 for a lone 50% child). */
    private fun mainAxisPercentTotal(vertical: Boolean): Float {
        var total = 0f
        for (i in 0..<childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue
            val lp = child.layoutParams as LayoutParams
            total += if (vertical) lp.maxHeightPercent else lp.maxWidthPercent
        }
        return total
    }

    /**
     * The cross-axis extent a deferred ratio child will end up being measured at.
     *
     * Settled before the main-axis solve runs — it comes from the length we were handed on the
     * other axis — which is what lets a ratio child count as fixed content in `S` even though it
     * isn't measured until the third pass.
     */
    private fun ratioCrossExtent(lp: LayoutParams, vertical: Boolean, crossSpecSize: Int): Int {
        val padding = if (vertical) paddingStart + paddingEnd else paddingTop + paddingBottom
        val margins = if (vertical) lp.marginStart + lp.marginEnd else lp.topMargin + lp.bottomMargin
        val percent = if (vertical) lp.maxWidthPercent else lp.maxHeightPercent
        val declared = if (vertical) lp.width else lp.height

        return when {
            percent > 0f -> ((crossSpecSize * percent).toInt() - margins).coerceAtLeast(0)
            declared != ViewGroup.LayoutParams.WRAP_CONTENT -> declared
            else -> (crossSpecSize - padding - margins).coerceAtLeast(0)
        }
    }

    /** The main-axis extent that cross extent implies, given the child's ratio. */
    private fun ratioMainExtent(lp: LayoutParams, vertical: Boolean, crossSpecSize: Int): Int {
        val cross = ratioCrossExtent(lp, vertical, crossSpecSize)
        return if (vertical) (cross / lp.aspectRatio).toInt() else (cross * lp.aspectRatio).toInt()
    }

    /**
     * The main-axis length percent children resolve against, or null to keep the measured sum.
     *
     * A percent child is a fraction of a length it also contributes to, so an auto-sized stack is
     * self-referential: `L = S + P*L`, which solves to `L = S / (1 - P)`. A 50% child beside a fixed
     * 200dp one makes the stack 400, not 300 — at 300 the child would be a third of it, not half.
     */
    private fun solveMainAxisLength(
        mode: Int,
        specSize: Int,
        fixedLength: Int,
        percentTotal: Float,
        borrowed: Int,
        autoAncestor: Boolean,
        hasLengthBasis: Boolean
    ): Int? {
        if (mode == MeasureSpec.EXACTLY || percentTotal <= 0f) return null

        // A scroll layout above us supplies the base; we still measure to our content.
        if (mode == MeasureSpec.UNSPECIFIED && borrowed > 0) return borrowed

        // Nothing here gives this axis a length, so there's nothing to take a percentage of.
        if (!hasLengthBasis) return null

        if (percentTotal >= 1f) {
            // No solution: the children want the whole stack or more, whatever it turns out to be.
            // Take everything on offer and let the distribution fair-share it, which is what iOS
            // does — a percent is a max with no min, so the fixed children are paid first and the
            // flexible ones split the rest. With nothing on offer there's nothing to divide, and
            // inherited slack only looks like an offer.
            return if (mode == MeasureSpec.AT_MOST && !autoAncestor) specSize else null
        }

        return (fixedLength / (1f - percentTotal)).roundToInt().coerceAtLeast(0)
    }

    private fun remeasureWithAspectRatio(
        child: View,
        lp: LayoutParams,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val ratio = lp.aspectRatio
        val widthAuto = lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxWidthPercent == 0f
        val heightAuto = lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxHeightPercent == 0f

        if (!widthAuto && !heightAuto) return

        val measuredW = child.measuredWidth
        val measuredH = child.measuredHeight

        val (newW, newH) = when {
            widthAuto && !heightAuto -> (measuredH * ratio).toInt() to measuredH
            !widthAuto && heightAuto -> measuredW to (measuredW / ratio).toInt()
            else -> when (orientation) {
                OrientationMode.VERTICAL -> {
                    val parentW = MeasureSpec.getSize(widthMeasureSpec)
                    if (parentW == 0) return
                    val avail = parentW - paddingStart - paddingEnd - lp.marginStart - lp.marginEnd
                    avail to (avail / ratio).toInt()
                }
                OrientationMode.HORIZONTAL -> {
                    val parentH = MeasureSpec.getSize(heightMeasureSpec)
                    if (parentH == 0) return
                    val avail = parentH - paddingTop - paddingBottom - lp.topMargin - lp.bottomMargin
                    (avail * ratio).toInt() to avail
                }
            }
        }

        child.measure(
            MeasureSpec.makeMeasureSpec(newW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(newH, MeasureSpec.EXACTLY)
        )
    }

    companion object {
        private const val ACCESSIBILITY_CLASS_NAME =
            "com.urbanairship.android.layout.widget.WeightlessLinearLayout"
    }
}
