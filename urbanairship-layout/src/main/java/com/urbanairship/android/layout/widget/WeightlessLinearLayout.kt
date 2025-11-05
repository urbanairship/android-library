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
) : ViewGroup(context, attrs, defStyleAttr) {

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
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

            if (lp.maxHeightPercent > 0) {
                childrenWithMaxPercent.add(child)
            }

            if (heightMode == MeasureSpec.EXACTLY && lp.height == 0 && lp.maxHeightPercent > 0) {
                // Optimization: don't bother measuring children who are going to use leftover space. These views will
                // get measured again down below if there is any leftover space.
                val totalLength = this.totalLength
                this.totalLength = max(totalLength, totalLength + lp.topMargin + lp.bottomMargin)
                skippedMeasure = true
            } else {
                var oldHeight = Int.Companion.MIN_VALUE
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    // heightMode is either UNSPECIFIED or AT_MOST, and this child wanted to stretch to fill available
                    // space. Translate that to WRAP_CONTENT so that it does not end up with a height of 0.
                    oldHeight = 0
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                val childHorizontalMargins = lp.marginStart + lp.marginEnd
                var oldWidth = Int.Companion.MIN_VALUE
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    oldWidth = 0
                    lp.width = (widthSize * lp.maxWidthPercent).toInt() - childHorizontalMargins
                }

                // Determine how big this child would like to be.
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    childHorizontalMargins,
                    heightMeasureSpec,
                    if (!childrenWithMaxPercent.isEmpty()) totalLength else 0
                )

                if (oldHeight != Int.Companion.MIN_VALUE) {
                    lp.height = oldHeight
                }

                if (oldWidth != Int.Companion.MIN_VALUE) {
                    lp.width = oldWidth
                }

                val childHeight = child.getMeasuredHeight()
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
            maxWidth = max(maxWidth, measuredWidth)
            childState = combineMeasuredStates(childState, child.measuredState)

            allFillParent = allFillParent && lp.width == ViewGroup.LayoutParams.MATCH_PARENT
            if (lp.maxHeightPercent > 0) {
                // Widths of max percentage Views are bogus if we end up remeasuring, so keep them separate.
                percentMaxWidth =
                    max(percentMaxWidth, if (matchWidthLocally) margin else measuredWidth)
            } else {
                alternativeMaxWidth =
                    max(alternativeMaxWidth, if (matchWidthLocally) margin else measuredWidth)
            }
        }

        // Add in our padding
        totalLength += paddingTop + paddingBottom

        // Check against our minimum height
        var height = totalLength
        height = max(height, suggestedMinimumHeight)

        // Reconcile our calculated size with the heightMeasureSpec
        val heightSizeAndState = resolveSizeAndState(height, heightMeasureSpec, 0)
        height = heightSizeAndState and MEASURED_SIZE_MASK

        // Either expand children with percentage dimensions to take up available space or shrink them if they extend
        // beyond our current bounds.
        var delta = height - totalLength

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

                    val newHeight = max(0, (originalHeights[i] * shrinkRatio).toInt())
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

            val lastChildIndex = maxPercentCount - 1
            for (i in 0..<maxPercentCount) {
                val child = childrenWithMaxPercent[i]
                val lp = child.layoutParams as LayoutParams

                if (heightMode != MeasureSpec.UNSPECIFIED) {
                    val actualPercent: kotlin.Float

                    if (delta >= (height * lp.maxHeightPercent) * (maxPercentCount - i)) {
                        actualPercent = lp.maxHeightPercent
                    } else {
                        actualPercent =
                            (delta.toFloat()) / ((childrenWithMaxPercent.size - i).toFloat()) / (height.toFloat())
                    }

                    var childHeight = (actualPercent * height).toInt()
                    if (i == lastChildIndex) {
                        childHeight = min(childHeight, delta)
                    }

                    delta -= childHeight

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

                    val heightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                    child.measure(widthSpec, heightSpec)

                    // Child may now not fit in vertical dimension.
                    childState = combineMeasuredStates(
                        childState,
                        child.measuredState and (MEASURED_STATE_MASK shr MEASURED_HEIGHT_STATE_SHIFT)
                    )
                }
            }

            // Determine width now that all views have been measured.
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

        if (!allFillParent && widthMode != MeasureSpec.EXACTLY) {
            maxWidth = alternativeMaxWidth
        }

        // Check against our minimum width
        maxWidth += paddingStart + paddingEnd
        maxWidth = max(maxWidth, suggestedMinimumWidth)

        setMeasuredDimension(
            resolveSizeAndState(maxWidth, widthMeasureSpec, childState), heightSizeAndState
        )

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

            if (lp.maxWidthPercent > 0) {
                childrenWithMaxPercent.add(child)
            }

            if (widthMode == MeasureSpec.EXACTLY && lp.width == 0 && lp.maxWidthPercent > 0) {
                // Optimization: don't bother measuring children who are going to use leftover space. These views will
                // get measured again down below if there is any leftover space.
                val totalLength = this.totalLength
                this.totalLength =
                    max(totalLength, totalLength + lp.marginStart + lp.marginEnd)
                skippedMeasure = true
            } else {
                var oldWidth = Int.Companion.MIN_VALUE
                if (lp.width == 0 && lp.maxWidthPercent > 0) {
                    // widthMode is either UNSPECIFIED or AT_MOST, and this child wanted to stretch to fill available
                    // space. Translate that to WRAP_CONTENT so that it does not end up with a width of 0.
                    oldWidth = 0
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                val childVerticalMargins = lp.topMargin + lp.bottomMargin
                var oldHeight = Int.Companion.MIN_VALUE
                if (lp.height == 0 && lp.maxHeightPercent > 0) {
                    oldHeight = 0
                    lp.height = (heightSize * lp.maxHeightPercent).toInt() - childVerticalMargins
                }

                // Determine how big this child would like to be.
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    if (!childrenWithMaxPercent.isEmpty()) totalLength else 0,
                    heightMeasureSpec,
                    childVerticalMargins
                )

                if (oldWidth != Int.Companion.MIN_VALUE) {
                    lp.width = oldWidth
                }

                if (oldHeight != Int.Companion.MIN_VALUE) {
                    lp.height = oldHeight
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
            maxHeight = max(maxHeight, measuredHeight)
            childState = combineMeasuredStates(childState, child.measuredState)

            allFillParent = allFillParent && lp.height == ViewGroup.LayoutParams.MATCH_PARENT
            if (lp.maxWidthPercent > 0) {
                // Heights of max percentage Views are bogus if we end up remeasuring, so keep them separate.
                percentMaxHeight =
                    max(percentMaxHeight, if (matchHeightLocally) margin else measuredHeight)
            } else {
                alternativeMaxHeight =
                    max(alternativeMaxHeight, if (matchHeightLocally) margin else measuredHeight)
            }
        }

        // Add in our padding
        totalLength += paddingStart + paddingEnd

        // Check against our minimum width
        var width = totalLength
        width = max(width, suggestedMinimumWidth)

        // Reconcile our calculated size with the heightMeasureSpec
        val widthSizeAndState = resolveSizeAndState(width, widthMeasureSpec, 0)
        width = widthSizeAndState and MEASURED_SIZE_MASK

        // Either expand children with percentage dimensions to take up available space or shrink them if they extend
        // beyond our current bounds.
        var delta = width - totalLength

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
                if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && child is ShrinkableView && (child as ShrinkableView).isShrinkable()) {
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

                    val newWidth = max(0, (originalWidths[i] * shrinkRatio).toInt())
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

            val lastChildIndex = maxPercentCount - 1
            for (i in 0..<maxPercentCount) {
                val child = childrenWithMaxPercent[i]
                val lp = child.layoutParams as LayoutParams

                if (widthMode != MeasureSpec.UNSPECIFIED) {
                    val actualPercent: kotlin.Float

                    if (delta >= (width * lp.maxWidthPercent) * (maxPercentCount - i)) {
                        actualPercent = lp.maxWidthPercent
                    } else {
                        actualPercent =
                            (delta.toFloat()) / ((childrenWithMaxPercent.size - i).toFloat()) / (width.toFloat())
                    }

                    var childWidth = (actualPercent * width).toInt()
                    if (i == lastChildIndex) {
                        childWidth = min(childWidth, delta)
                    }

                    delta -= childWidth

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

                    val widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                    child.measure(widthSpec, heightSpec)

                    // Child may now not fit in horizontal dimension.
                    childState = combineMeasuredStates(
                        childState, child.measuredState and MEASURED_STATE_MASK
                    )
                }
            }

            // Determine height now that all views have been measured.
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

        if (!allFillParent && heightMode != MeasureSpec.EXACTLY) {
            maxHeight = alternativeMaxHeight
        }

        // Check against our minimum height
        maxHeight += paddingTop + paddingBottom
        maxHeight = max(maxHeight, suggestedMinimumHeight)

        setMeasuredDimension(
            widthSizeAndState, resolveSizeAndState(maxHeight, heightMeasureSpec, childState)
        )

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
            val layoutDirection = ViewCompat.getLayoutDirection(this)
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
        val isLayoutRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
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

        val layoutDirection = ViewCompat.getLayoutDirection(this)
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
            width: Int, height: Int, maxWidthPercent: kotlin.Float, maxHeightPercent: kotlin.Float
        ) : super(width, height) {
            this.maxWidthPercent = maxWidthPercent
            this.maxHeightPercent = maxHeightPercent
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
                "LayoutParams{ width = %d, height = %d, maxWidth = %.2f, maxHeight = %.2f }",
                width,
                height,
                maxWidthPercent,
                maxHeightPercent
            )
        }
    }

     companion object {
        private const val ACCESSIBILITY_CLASS_NAME =
            "com.urbanairship.android.layout.widget.WeightlessLinearLayout"
    }
}
