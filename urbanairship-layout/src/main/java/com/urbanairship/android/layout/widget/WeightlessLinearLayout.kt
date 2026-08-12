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
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Variant of `LinearLayout` that replaces weight with max percentage sizes.
 *
 *
 * If any children specify max percents, any remaining space in the layout will be allocated evenly, up to the max size.
 * @hide
 */
internal open class WeightlessLinearLayout @JvmOverloads public constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr), AutoSizeProvider {

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

        var matchWidth = false
        var skippedMeasure = false

        // See how tall everyone is. Also remember max width.
        for (i in 0..<count) {
            val child = getChildAt(i) ?: continue

            if (child.visibility == GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams

            // Any height-auto + ratio in a bounded layout: defer to a third pass so ratio items
            // never overflow the space remaining after siblings are measured.
            if (lp.aspectRatio > 0f
                && lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxHeightPercent == 0f
                && (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST)) {
                ratioFillChildren.add(child)
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

                // Determine how big this child would like to be.
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    childHorizontalMargins,
                    heightMeasureSpec,
                    if (!childrenWithMaxPercent.isEmpty()) totalLength else 0
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
            // otherwise the percentage would end up describing a length it set itself.
            if (child !in crossAxisPercentChildren) {
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

        // Add in our padding
        totalLength += paddingTop + paddingBottom
        val totalLengthAfterFirstPass = totalLength

        // `S` — the fixed content: everything measured above, less the percent children's margins,
        // which the distribution pass takes out of their own slots rather than up front.
        val percentTotal = mainAxisPercentTotal(vertical = true)
        val percentMargins = childrenWithMaxPercent.sumOf {
            val lp = it.layoutParams as LayoutParams
            lp.topMargin + lp.bottomMargin
        }

        // Check against our minimum height
        val solvedHeight = solveMainAxisLength(
            mode = heightMode,
            specSize = MeasureSpec.getSize(heightMeasureSpec),
            fixedLength = totalLength - percentMargins,
            percentTotal = percentTotal,
            borrowed = if (percentTotal > 0f) borrowedPercentBase(horizontal = false) else 0,
            autoAncestor = percentTotal >= 1f && hasAutoSizedAncestor(horizontal = false)
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

                val lp = child.layoutParams as LayoutParams
                if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxHeightPercent == 0f
                    && lp.aspectRatio == 0f
                    && child is ShrinkableView && (child as ShrinkableView).isShrinkable()
                ) {
                    shrinkableChildren.add(child)

                    val childHeight = child.measuredHeight
                    originalHeights.add(childHeight)
                    totalShrinkableHeight += childHeight
                }
            }

            if (!shrinkableChildren.isEmpty() && totalShrinkableHeight >= abs(delta)) {
                val shrinkRatio = (totalShrinkableHeight + delta).toFloat() / totalShrinkableHeight
                // Remeasure with reduced heights
                for (i in shrinkableChildren.indices) {
                    val child = shrinkableChildren.get(i)
                    val lp = child.layoutParams as LayoutParams

                    val newHeight = max(0, (originalHeights[i] * shrinkRatio).roundToInt())
                    val originalWidth = child.measuredWidth

                    // Preserve original width based on layout params
                    val widthSpec: Int
                    if (lp.width == 0 && lp.maxWidthPercent > 0) {
                        val childWidth: Int
                        if (widthSize == 0 && widthMode == MeasureSpec.UNSPECIFIED) {
                            childWidth = ViewGroup.LayoutParams.WRAP_CONTENT
                            widthSpec =
                                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.UNSPECIFIED)
                        } else {
                            childWidth =
                                (widthSize * lp.maxWidthPercent).toInt() - (lp.marginStart + lp.marginEnd)
                            widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                        }
                    } else if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT && widthMode != MeasureSpec.EXACTLY) {
                        widthSpec = MeasureSpec.makeMeasureSpec(originalWidth, MeasureSpec.EXACTLY)
                    } else {
                        widthSpec = getChildMeasureSpec(
                            widthMeasureSpec, lp.marginStart + lp.marginEnd, lp.width
                        )
                    }

                    // Remeasure with new height
                    val heightSpec = MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY)
                    child.measure(widthSpec, heightSpec)
                }

                // Recalculate totalLength
                totalLength = 0
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) {
                        continue
                    }

                    val lp = child.layoutParams as LayoutParams
                    totalLength += child.measuredHeight + lp.topMargin + lp.bottomMargin
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
            var slotRemaining = delta + percentMargins

            val lastChildIndex = maxPercentCount - 1
            for (i in 0..<maxPercentCount) {
                val child = childrenWithMaxPercent[i]
                val lp = child.layoutParams as LayoutParams

                val widthSpec: Int
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    val childWidth: Int
                    if (widthSize == 0 && widthMode == MeasureSpec.UNSPECIFIED) {
                        childWidth = ViewGroup.LayoutParams.WRAP_CONTENT
                        widthSpec =
                            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.UNSPECIFIED)
                    } else {
                        childWidth =
                            (widthSize * lp.maxWidthPercent).toInt() - (lp.marginStart + lp.marginEnd)
                        widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
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
                maxWidth = max(maxWidth, measuredWidth)

                val matchWidthLocally =
                    widthMode != MeasureSpec.EXACTLY && lp.width == ViewGroup.LayoutParams.MATCH_PARENT

                alternativeMaxWidth =
                    max(alternativeMaxWidth, if (matchWidthLocally) margin else measuredWidth)

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

        // Sizing the percent children changed how tall we are. Unless the spec fixed our height,
        // report the content — in particular, when the base was a borrowed viewport we may well be
        // taller than it, which is the whole point of being inside a scroll.
        if (heightMode != MeasureSpec.EXACTLY && percentTotal > 0f) {
            heightSizeAndState = resolveSizeAndState(totalLength, heightMeasureSpec, childState)
        }

        // Third pass: measure deferred ratio children using the remaining available height so they
        // never push siblings off screen. For AT_MOST parents the bound is the spec size minus what
        // siblings consumed; for EXACTLY the bound is what's left of the fixed parent height.
        if (ratioFillChildren.isNotEmpty()) {
            val availW = widthSize - paddingStart - paddingEnd
            val boundedParentH = if (heightMode == MeasureSpec.AT_MOST)
                MeasureSpec.getSize(heightMeasureSpec) else height
            val remaining = (boundedParentH - totalLengthAfterFirstPass).coerceAtLeast(0)
            var totalRatioHeight = 0

            // Per-child ideal sizes (idealW = column/declared width, idealH = idealW / ratio).
            val idealWs = IntArray(ratioFillChildren.size)
            val idealHs = IntArray(ratioFillChildren.size)
            for (i in ratioFillChildren.indices) {
                val lp = ratioFillChildren[i].layoutParams as LayoutParams
                val idealW = when {
                    lp.maxWidthPercent > 0f ->
                        ((widthSize * lp.maxWidthPercent).toInt() - lp.marginStart - lp.marginEnd).coerceAtLeast(0)
                    lp.width != ViewGroup.LayoutParams.WRAP_CONTENT -> lp.width
                    else -> (availW - lp.marginStart - lp.marginEnd).coerceAtLeast(0)
                }
                idealWs[i] = idealW
                idealHs[i] = (idealW / lp.aspectRatio).toInt()
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
                totalRatioHeight += child.measuredHeight + lp.topMargin + lp.bottomMargin
            }

            // For AT_MOST, the first-pass height excluded ratio children; update it now.
            if (heightMode == MeasureSpec.AT_MOST) {
                val actualTotal = totalLengthAfterFirstPass + totalRatioHeight
                heightSizeAndState = resolveSizeAndState(actualTotal, heightMeasureSpec, childState)
            }
        }

        if (!allFillParent && widthMode != MeasureSpec.EXACTLY) {
            maxWidth = alternativeMaxWidth
        }

        // Check against our minimum width
        maxWidth += paddingStart + paddingEnd
        maxWidth = max(maxWidth, suggestedMinimumWidth)

        setMeasuredDimension(
            resolveSizeAndState(maxWidth, widthMeasureSpec, childState), heightSizeAndState
        )

        // Now that our width is settled, give the deferred cross-axis percent children their share
        // of it. Heights are pinned to what they already measured so this can't move the stack
        // length we just reported — the same bargain `forceUniformWidth` makes below.
        if (crossAxisPercentChildren.isNotEmpty()) {
            val available = (measuredWidth - paddingStart - paddingEnd).coerceAtLeast(0)
            for (child in crossAxisPercentChildren) {
                val lp = child.layoutParams as LayoutParams
                val margins = lp.marginStart + lp.marginEnd
                val childWidth = (minOf((available * lp.maxWidthPercent).toInt(), available) - margins)
                    .coerceAtLeast(0)

                child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
                )
            }
        }

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

        var matchHeight = false
        var skippedMeasure = false

        // See how wide everyone is. Also remember max height.
        for (i in 0..<count) {
            val child = getChildAt(i) ?: continue

            if (child.visibility == GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams

            // Any width-auto + ratio in a bounded layout: defer to a third pass so ratio items
            // never overflow the space remaining after siblings are measured.
            if (lp.aspectRatio > 0f
                && lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && lp.maxWidthPercent == 0f
                && (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST)) {
                ratioFillChildren.add(child)
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

                // Determine how big this child would like to be.
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    if (!childrenWithMaxPercent.isEmpty()) totalLength else 0,
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
            // otherwise the percentage would end up describing a length it set itself.
            if (child !in crossAxisPercentChildren) {
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

        // Add in our padding
        totalLength += paddingStart + paddingEnd
        val totalLengthAfterFirstPass = totalLength

        // `S` — the fixed content: everything measured above, less the percent children's margins,
        // which the distribution pass takes out of their own slots rather than up front.
        val percentTotal = mainAxisPercentTotal(vertical = false)
        val percentMargins = childrenWithMaxPercent.sumOf {
            val lp = it.layoutParams as LayoutParams
            lp.marginStart + lp.marginEnd
        }

        // Check against our minimum width
        val solvedWidth = solveMainAxisLength(
            mode = widthMode,
            specSize = MeasureSpec.getSize(widthMeasureSpec),
            fixedLength = totalLength - percentMargins,
            percentTotal = percentTotal,
            borrowed = if (percentTotal > 0f) borrowedPercentBase(horizontal = true) else 0,
            autoAncestor = percentTotal >= 1f && hasAutoSizedAncestor(horizontal = true)
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

                val lp = child.layoutParams as LayoutParams
                if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && lp.aspectRatio == 0f
                    && child is ShrinkableView && (child as ShrinkableView).isShrinkable()) {
                    shrinkableChildren.add(child)

                    val childWidth = child.measuredWidth
                    originalWidths.add(childWidth)
                    totalShrinkableWidth += childWidth
                }
            }

            if (!shrinkableChildren.isEmpty() && totalShrinkableWidth >= abs(delta)) {
                val shrinkRatio = (totalShrinkableWidth + delta).toFloat() / totalShrinkableWidth
                // Remeasure with reduced heights
                for (i in shrinkableChildren.indices) {
                    val child = shrinkableChildren[i]
                    val lp = child.layoutParams as LayoutParams

                    val newWidth = max(0, (originalWidths[i] * shrinkRatio).roundToInt())
                    val originalHeight = child.measuredHeight

                    // Preserve original height based on layout params
                    val heightSpec: Int
                    if (lp.height == 0 && lp.maxHeightPercent > 0) {
                        val childHeight: Int
                        if (heightSize == 0 && heightMode == MeasureSpec.UNSPECIFIED) {
                            childHeight = ViewGroup.LayoutParams.WRAP_CONTENT
                            heightSpec =
                                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.UNSPECIFIED)
                        } else {
                            childHeight =
                                (heightSize * lp.maxHeightPercent).toInt() - (lp.topMargin + lp.bottomMargin)
                            heightSpec =
                                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                        }
                    } else if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT && heightMode != MeasureSpec.EXACTLY) {
                        heightSpec =
                            MeasureSpec.makeMeasureSpec(originalHeight, MeasureSpec.EXACTLY)
                    } else {
                        heightSpec = getChildMeasureSpec(
                            heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height
                        )
                    }

                    // Remeasure with new height
                    val widthSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
                    child.measure(widthSpec, heightSpec)
                }

                // Recalculate totalLength
                totalLength = 0
                for (i in 0..<count) {
                    val child = getChildAt(i) ?: continue
                    if (child.visibility == GONE) {
                        continue
                    }

                    val lp = child.layoutParams as LayoutParams
                    totalLength += child.measuredWidth + lp.marginStart + lp.marginEnd
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
            var slotRemaining = delta + percentMargins

            val lastChildIndex = maxPercentCount - 1
            for (i in 0..<maxPercentCount) {
                val child = childrenWithMaxPercent[i]
                val lp = child.layoutParams as LayoutParams

                val heightSpec: Int
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    val childHeight: Int
                    if (heightSize == 0 && heightMode == MeasureSpec.UNSPECIFIED) {
                        childHeight = ViewGroup.LayoutParams.WRAP_CONTENT
                        heightSpec =
                            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.UNSPECIFIED)
                    } else {
                        childHeight =
                            (heightSize * lp.maxHeightPercent).toInt() - (lp.topMargin + lp.bottomMargin)
                        heightSpec =
                            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
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
                maxHeight = max(maxHeight, measuredHeight)

                val matchHeightLocally =
                    widthMode != MeasureSpec.EXACTLY && lp.height == ViewGroup.LayoutParams.MATCH_PARENT

                alternativeMaxHeight =
                    max(alternativeMaxHeight, if (matchHeightLocally) margin else measuredHeight)

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

        // Sizing the percent children changed how wide we are. Unless the spec fixed our width,
        // report the content — in particular, when the base was a borrowed viewport we may well be
        // wider than it, which is the whole point of being inside a scroll.
        if (widthMode != MeasureSpec.EXACTLY && percentTotal > 0f) {
            widthSizeAndState = resolveSizeAndState(totalLength, widthMeasureSpec, childState)
        }

        // Third pass: measure deferred ratio children using the remaining available width so they
        // never push siblings off screen.
        if (ratioFillChildren.isNotEmpty()) {
            val availH = heightSize - paddingTop - paddingBottom
            val boundedParentW = if (widthMode == MeasureSpec.AT_MOST)
                MeasureSpec.getSize(widthMeasureSpec) else width
            val remaining = (boundedParentW - totalLengthAfterFirstPass).coerceAtLeast(0)
            var totalRatioWidth = 0

            val idealHs = IntArray(ratioFillChildren.size)
            val idealWs = IntArray(ratioFillChildren.size)
            for (i in ratioFillChildren.indices) {
                val lp = ratioFillChildren[i].layoutParams as LayoutParams
                val idealH = when {
                    lp.maxHeightPercent > 0f ->
                        ((heightSize * lp.maxHeightPercent).toInt() - lp.topMargin - lp.bottomMargin).coerceAtLeast(0)
                    lp.height != ViewGroup.LayoutParams.WRAP_CONTENT -> lp.height
                    else -> (availH - lp.topMargin - lp.bottomMargin).coerceAtLeast(0)
                }
                idealHs[i] = idealH
                idealWs[i] = (idealH * lp.aspectRatio).toInt()
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
                totalRatioWidth += child.measuredWidth + lp.marginStart + lp.marginEnd
            }

            if (widthMode == MeasureSpec.AT_MOST) {
                val actualTotal = totalLengthAfterFirstPass + totalRatioWidth
                widthSizeAndState = resolveSizeAndState(actualTotal, widthMeasureSpec, childState)
            }
        }

        if (!allFillParent && heightMode != MeasureSpec.EXACTLY) {
            maxHeight = alternativeMaxHeight
        }

        // Check against our minimum height
        maxHeight += paddingTop + paddingBottom
        maxHeight = max(maxHeight, suggestedMinimumHeight)

        setMeasuredDimension(
            widthSizeAndState, resolveSizeAndState(maxHeight, heightMeasureSpec, childState)
        )

        // Now that our height is settled, give the deferred cross-axis percent children their share
        // of it. Widths are pinned to what they already measured so this can't move the stack
        // length we just reported — the same bargain `forceUniformHeight` makes below.
        if (crossAxisPercentChildren.isNotEmpty()) {
            val available = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)
            for (child in crossAxisPercentChildren) {
                val lp = child.layoutParams as LayoutParams
                val margins = lp.topMargin + lp.bottomMargin
                val childHeight = (minOf((available * lp.maxHeightPercent).toInt(), available) - margins)
                    .coerceAtLeast(0)

                child.measure(
                    MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                )
            }
        }

        if (matchHeight) {
            forceUniformHeight(count, widthMeasureSpec)
        }
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
        val isLayoutRtl = layoutDirection == LAYOUT_DIRECTION_RTL
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
     * The stack-axis length percent children resolve against, or null to keep the measured sum.
     *
     * A percent child is a fraction of a length it also contributes to, so an auto-sized stack is
     * self-referential: `H = S + P*H`, which solves to `H = S / (1 - P)`. A 50% child beside a
     * fixed 200dp one makes the stack 400 tall, not 300 — at 300 the child would be 100, a third
     * of the stack rather than half of it.
     *
     * Unbounded, the base is a viewport borrowed from a scroll layout above us, matching iOS where
     * the scroll fills its content's auto dimension with its own frame. The stack still measures to
     * its content, so it can be taller than that base and scroll.
     *
     * Percentages summing to 1 or more have no solution — the children want more than the whole
     * stack whatever it turns out to be — so the measured sum stands and the distribution pass
     * fair-shares what's actually there. That matches iOS, where a percent is a `maxHeight` with no
     * `minHeight`: fixed children are paid first and the flexible ones split the remainder.
     *
     * That last case only works against a length that is honestly ours to divide. [autoAncestor]
     * says it isn't: an auto-sized ancestor is passing its own content budget through as a ceiling,
     * and a stack that has no length of its own can't claim the budget it's supposed to be
     * contributing to. Taking it starves every sibling — the fixed ones overflow the parent, which
     * collapses the auto ones to nothing. Nil is the honest answer; percent children with no length
     * to resolve against fall back to their content, which is stable and matches iOS.
     */
    private fun solveMainAxisLength(
        mode: Int,
        specSize: Int,
        fixedLength: Int,
        percentTotal: Float,
        borrowed: Int,
        autoAncestor: Boolean
    ): Int? {
        if (mode == MeasureSpec.EXACTLY || percentTotal <= 0f) return null

        // A scroll layout above us supplies the base; we still measure to our content.
        if (mode == MeasureSpec.UNSPECIFIED && borrowed > 0) return borrowed

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
