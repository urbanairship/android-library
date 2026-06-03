/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.Context
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.ConstrainedSize.ConstrainedDimensionType
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.VerticalPosition
import kotlin.math.roundToInt

public class ConstraintSetBuilder private constructor(
    private val context: Context,
    public val constraints: ConstraintSet = ConstraintSet()
) {

    public fun constrainWithinParent(viewId: Int, margin: Margin? = null): ConstraintSetBuilder {
        if (margin == null) {
            constraints.addToHorizontalChain(
                viewId, ConstraintSet.PARENT_ID, ConstraintSet.PARENT_ID
            )
            constraints.addToVerticalChain(viewId, ConstraintSet.PARENT_ID, ConstraintSet.PARENT_ID)
        } else {
            addToHorizontalChain(
                viewId, ConstraintSet.PARENT_ID, ConstraintSet.PARENT_ID, margin.start, margin.end
            )
            addToVerticalChain(
                viewId, ConstraintSet.PARENT_ID, ConstraintSet.PARENT_ID, margin.top, margin.bottom
            )
        }
        return this
    }

    /**
     * Adds a view to a vertical chain.
     *
     * @param viewId view to add to a vertical chain
     * @param topId view above.
     * @param bottomId view below
     */
    public fun addToVerticalChain(
        viewId: Int, topId: Int, bottomId: Int, marginTop: Int, marginBottom: Int
    ): ConstraintSetBuilder {
        constraints.connect(
            viewId,
            ConstraintSet.TOP,
            topId,
            if (topId == ConstraintSet.PARENT_ID) ConstraintSet.TOP else ConstraintSet.BOTTOM,
            ResourceUtils.dpToPx(context, marginTop).toInt()
        )
        constraints.connect(
            viewId,
            ConstraintSet.BOTTOM,
            bottomId,
            if (bottomId == ConstraintSet.PARENT_ID) ConstraintSet.BOTTOM else ConstraintSet.TOP,
            ResourceUtils.dpToPx(context, marginBottom).toInt()
        )
        if (topId != ConstraintSet.PARENT_ID) {
            constraints.connect(topId, ConstraintSet.BOTTOM, viewId, ConstraintSet.TOP, 0)
        }
        if (bottomId != ConstraintSet.PARENT_ID) {
            constraints.connect(bottomId, ConstraintSet.TOP, viewId, ConstraintSet.BOTTOM, 0)
        }

        return this
    }

    /**
     * Adds a view to a horizontal chain.
     *
     * @param viewId view to add
     * @param leftId view in chain to the left
     * @param rightId view in chain to the right
     */
    public fun addToHorizontalChain(
        viewId: Int, leftId: Int, rightId: Int, leftMargin: Int, rightMargin: Int
    ): ConstraintSetBuilder {
        constraints.connect(
            viewId,
            ConstraintSet.LEFT,
            leftId,
            if (leftId == ConstraintSet.PARENT_ID) ConstraintSet.LEFT else ConstraintSet.RIGHT,
            ResourceUtils.dpToPx(context, leftMargin).toInt()
        )
        constraints.connect(
            viewId,
            ConstraintSet.RIGHT,
            rightId,
            if (rightId == ConstraintSet.PARENT_ID) ConstraintSet.RIGHT else ConstraintSet.LEFT,
            ResourceUtils.dpToPx(context, rightMargin).toInt()
        )
        if (leftId != ConstraintSet.PARENT_ID) {
            constraints.connect(leftId, ConstraintSet.RIGHT, viewId, ConstraintSet.LEFT, 0)
        }
        if (rightId != ConstraintSet.PARENT_ID) {
            constraints.connect(rightId, ConstraintSet.LEFT, viewId, ConstraintSet.RIGHT, 0)
        }
        return this
    }

    public fun setHorizontalChainStyle(viewIds: IntArray, chainStyle: Int): ConstraintSetBuilder {
        viewIds.forEach { constraints.setHorizontalChainStyle(it, chainStyle) }
        return this
    }

    public fun createHorizontalChainInParent(
        viewIds: IntArray, verticalSpacing: Int, horizontalSpacing: Int
    ): ConstraintSetBuilder {
        for (i in viewIds.indices) {
            val viewId = viewIds[i]
            when (i) {
                0 -> {
                    addToHorizontalChain(
                        viewId, ConstraintSet.PARENT_ID, viewIds[i + 1], 0, horizontalSpacing
                    )
                }
                viewIds.size - 1 -> {
                    addToHorizontalChain(
                        viewId, viewIds[i - 1], ConstraintSet.PARENT_ID, horizontalSpacing, 0
                    )
                }
                else -> {
                    addToHorizontalChain(
                        viewId, viewIds[i - 1], viewIds[i + 1], horizontalSpacing, horizontalSpacing
                    )
                }
            }

            addToVerticalChain(
                viewId,
                ConstraintSet.PARENT_ID,
                ConstraintSet.PARENT_ID,
                verticalSpacing,
                verticalSpacing
            )
        }

        return this
    }

    public fun squareAspectRatio(viewId: Int): ConstraintSetBuilder {
        constraints.setDimensionRatio(viewId, "1:1")
        return this
    }

    public fun minWidth(viewId: Int, minWidth: Int): ConstraintSetBuilder {
        constraints.constrainMinWidth(viewId, ResourceUtils.dpToPx(context, minWidth).toInt())
        return this
    }

    public fun maxWidth(viewId: Int, maxWidth: Int): ConstraintSetBuilder {
        constraints.constrainMaxWidth(viewId, ResourceUtils.dpToPx(context, maxWidth).toInt())
        return this
    }

    public fun minHeight(viewId: Int, minHeight: Int): ConstraintSetBuilder {
        constraints.constrainMinHeight(viewId, ResourceUtils.dpToPx(context, minHeight).toInt())
        return this
    }

    public fun maxHeight(viewId: Int, maxHeight: Int): ConstraintSetBuilder {
        constraints.constrainMaxHeight(viewId, ResourceUtils.dpToPx(context, maxHeight).toInt())
        return this
    }

    public fun size(size: Size?, @IdRes viewId: Int): ConstraintSetBuilder {
        return size(size, false, viewId)
    }

    @JvmOverloads
    public fun size(
        size: Size?,
        ignoreSafeArea: Boolean,
        @IdRes viewId: Int,
        autoValue: Int = ConstraintSet.WRAP_CONTENT
    ): ConstraintSetBuilder {
        width(size, ignoreSafeArea, viewId, autoValue)
        height(size, ignoreSafeArea, viewId, autoValue)
        return aspectRatio(size, viewId, ignoreSafeArea)
    }

    /**
     * Applies [Size.aspectRatio] after width/height constraints have been set.
     *
     * For one-auto cases the non-auto dimension is already set by [width]/[height] (as a real
     * ConstraintLayout percent or absolute constraint, which is always parent-relative); the
     * auto dimension is forced to MATCH_CONSTRAINT and derived from it via a directional ratio
     * prefix (`"H,ratio"` when width is known, `"W,ratio"` when height is known). For both-auto
     * the ratio has no prefix so ConstraintLayout fits the largest box within the parent.
     * `constrainedWidth`/`constrainedHeight` caps the derived dimension at the parent's bounds.
     *
     * Note: percent dimensions are deliberately NOT converted to a window-pixel max here — that
     * is screen-relative and wrong for items nested in smaller parents (see git history around
     * c548af9d). Percent is resolved by ConstraintLayout against the actual parent.
     */
    @JvmOverloads
    public fun aspectRatio(size: Size?, @IdRes viewId: Int, ignoreSafeArea: Boolean = false): ConstraintSetBuilder {
        val ratio = size?.aspectRatio ?: return this
        val widthAuto = size.width.isAuto
        val heightAuto = size.height.isAuto

        when {
            widthAuto && heightAuto -> {
                constraints.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainedWidth(viewId, true)
                constraints.constrainedHeight(viewId, true)
                constraints.setDimensionRatio(viewId, "$ratio:1")
            }
            widthAuto -> {
                // Height is the known dimension (percent or absolute); derive width from it.
                constraints.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainedWidth(viewId, true)
                constraints.setDimensionRatio(viewId, "W,$ratio:1")
            }
            heightAuto -> {
                // Width is the known dimension (percent or absolute); derive height from it.
                constraints.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainedHeight(viewId, true)
                constraints.setDimensionRatio(viewId, "H,$ratio:1")
            }
            // Both dimensions fixed — ratio is ignored per spec.
        }
        return this
    }

    /**
     * Bounds-aware variant of [aspectRatio] for window-parented presentations (modal/banner).
     *
     * For the one-auto case, [aspectRatio] sets a directional dimension ratio
     * (`"H,ratio:1"` / `"W,ratio:1"`) which ConstraintLayout resolves with *priority over*
     * `constrainedWidth`/`constrainedHeight`, so the derived dimension can overflow the parent
     * (e.g. a `height: auto`, `width: 90%`, `aspect_ratio: 1.778` modal in landscape derives a
     * height taller than the screen). This method detects that overflow and instead bakes the
     * largest ratio-preserving box that fits *both* available bounds as explicit pixels — matching
     * iOS's `.aspectRatio(.fit)`. When the requested rect already fits, behavior is byte-identical
     * to [aspectRatio]'s one-auto branch.
     *
     * Two bases per axis: the **window** dim is the percent base (matching what [width]/[height]
     * actually render via `constrainPercent*`), while the margin-reduced **available** dim is the
     * overflow bound and fitted-size target.
     *
     * Both-auto and both-fixed delegate to the unchanged [aspectRatio]. This method is *not* used
     * by embedded/container views, where window-pixel reasoning is wrong for nested parents.
     */
    @JvmOverloads
    public fun aspectRatioWithinBounds(
        size: Size?,
        @IdRes viewId: Int,
        windowWidthPx: Int,
        windowHeightPx: Int,
        availableWidthPx: Int,
        availableHeightPx: Int,
        ignoreSafeArea: Boolean = false
    ): ConstraintSetBuilder {
        val ratio = size?.aspectRatio ?: return this
        val widthAuto = size.width.isAuto
        val heightAuto = size.height.isAuto

        // Both-auto and both-fixed: delegate to the shared path (unchanged).
        if (widthAuto == heightAuto) {
            return aspectRatio(size, viewId, ignoreSafeArea)
        }

        // One-auto: resolve the known dimension against the full window (matching what
        // width()/height() rendered) and derive the other from the ratio.
        val requestedWidthPx: Double
        val requestedHeightPx: Double
        if (heightAuto) {
            val knownWidthPx = resolveKnownDimensionPx(size.width, windowWidthPx)
            requestedWidthPx = knownWidthPx
            requestedHeightPx = knownWidthPx / ratio
        } else {
            val knownHeightPx = resolveKnownDimensionPx(size.height, windowHeightPx)
            requestedHeightPx = knownHeightPx
            requestedWidthPx = knownHeightPx * ratio
        }

        val fit = computeAspectRatioFit(
            requestedWidthPx, requestedHeightPx, ratio, availableWidthPx, availableHeightPx
        )

        if (fit == null) {
            // Fits — identical to aspectRatio()'s one-auto branch.
            if (heightAuto) {
                constraints.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainedHeight(viewId, true)
                constraints.setDimensionRatio(viewId, "H,$ratio:1")
            } else {
                constraints.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainedWidth(viewId, true)
                constraints.setDimensionRatio(viewId, "W,$ratio:1")
            }
        } else {
            // Overflow — bake the fitted px box. A positive width/height makes the dimension
            // fixed, overriding the MATCH_CONSTRAINT-percent default left by width()/height().
            // The ratio is preserved exactly by the px math, so no dimension ratio is set.
            constraints.constrainWidth(viewId, fit.widthPx)
            constraints.constrainHeight(viewId, fit.heightPx)
        }
        return this
    }

    /** Resolves a known (non-auto) dimension to px against the full window dimension. */
    private fun resolveKnownDimensionPx(dimension: Size.Dimension, windowDimPx: Int): Double =
        when (dimension.type) {
            Size.DimensionType.PERCENT -> dimension.getFloat().toDouble() * windowDimPx
            Size.DimensionType.ABSOLUTE -> ResourceUtils.dpToPx(context, dimension.getInt()).toDouble()
            // Not reached in the one-auto path; treat as full window.
            Size.DimensionType.AUTO -> windowDimPx.toDouble()
        }

    public fun width(size: Size?, @IdRes viewId: Int): ConstraintSetBuilder {
        return width(size, false, viewId)
    }

    @JvmOverloads
    public fun width(
        size: Size?,
        ignoreSafeArea: Boolean,
        @IdRes viewId: Int,
        autoValue: Int = ConstraintSet.WRAP_CONTENT
    ): ConstraintSetBuilder {
        if (size == null) {
            return this
        }

        if (size is ConstrainedSize) {
            size.minWidth?.let { minWidth ->
                when (minWidth.type) {
                    ConstrainedDimensionType.PERCENT -> {
                        val minPixelsWidth =
                            minWidth.getFloat() * ResourceUtils.getWindowWidthPixels(
                                context, ignoreSafeArea
                            )
                        constraints.constrainMinWidth(viewId, minPixelsWidth.toInt())
                    }

                    ConstrainedDimensionType.ABSOLUTE -> constraints.constrainMinWidth(
                        viewId, ResourceUtils.dpToPx(context, minWidth.getInt()).toInt()
                    )
                }
            }

            size.maxWidth?.let { maxWidth ->
                when (maxWidth.type) {
                    ConstrainedDimensionType.PERCENT -> {
                        val maxPixelsWidth =
                            maxWidth.getFloat() * ResourceUtils.getWindowWidthPixels(
                                context, ignoreSafeArea
                            )
                        constraints.constrainMaxWidth(viewId, maxPixelsWidth.toInt())
                    }

                    ConstrainedDimensionType.ABSOLUTE -> constraints.constrainMaxWidth(
                        viewId, ResourceUtils.dpToPx(context, maxWidth.getInt()).toInt()
                    )
                }
            }
        }

        val width = size.width
        when (width.type) {
            Size.DimensionType.AUTO -> constraints.constrainWidth(viewId, autoValue)
            Size.DimensionType.PERCENT -> if (width.getFloat() == 1f) {
                constraints.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
            } else {
                constraints.constrainPercentWidth(viewId, width.getFloat())
            }

            Size.DimensionType.ABSOLUTE -> constraints.constrainWidth(
                viewId, ResourceUtils.dpToPx(context, width.getInt()).toInt()
            )
        }

        return this
    }

    public fun height(size: Size?, @IdRes viewId: Int): ConstraintSetBuilder {
        return height(size, false, viewId)
    }

    @JvmOverloads
    public fun height(
        size: Size?,
        ignoreSafeArea: Boolean,
        @IdRes viewId: Int,
        autoValue: Int = ConstraintSet.WRAP_CONTENT
    ): ConstraintSetBuilder {
        if (size == null) {
            return this
        }

        if (size is ConstrainedSize) {
            size.minHeight?.let { minHeight ->
                when (minHeight.type) {
                    ConstrainedDimensionType.PERCENT -> {
                        val minPixelsHeight =
                            minHeight.getFloat() * ResourceUtils.getWindowHeightPixels(
                                context, ignoreSafeArea
                            )
                        constraints.constrainMinHeight(viewId, minPixelsHeight.toInt())
                    }

                    ConstrainedDimensionType.ABSOLUTE -> constraints.constrainMinHeight(
                        viewId, ResourceUtils.dpToPx(context, minHeight.getInt()).toInt()
                    )
                }
            }

            size.maxHeight?.let { maxHeight ->
                when (maxHeight.type) {
                    ConstrainedDimensionType.PERCENT -> {
                        val maxPixelsHeight =
                            maxHeight.getFloat() * ResourceUtils.getWindowHeightPixels(
                                context, ignoreSafeArea
                            )
                        constraints.constrainMaxHeight(viewId, maxPixelsHeight.toInt())
                    }

                    ConstrainedDimensionType.ABSOLUTE -> constraints.constrainMaxHeight(
                        viewId, ResourceUtils.dpToPx(context, maxHeight.getInt()).toInt()
                    )
                }
            }
        }

        val height = size.height
        when (height.type) {
            Size.DimensionType.AUTO -> constraints.constrainHeight(viewId, autoValue)
            Size.DimensionType.PERCENT -> if (height.getFloat() == 1f) {
                constraints.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
            } else {
                constraints.constrainPercentHeight(viewId, height.getFloat())
            }

            Size.DimensionType.ABSOLUTE -> constraints.constrainHeight(
                viewId, ResourceUtils.dpToPx(context, height.getInt()).toInt()
            )
        }

        return this
    }

    public fun matchConstraintWidth(viewId: Int): ConstraintSetBuilder {
        constraints.constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
        return this
    }

    public fun matchConstraintHeight(viewId: Int): ConstraintSetBuilder {
        constraints.constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
        return this
    }

    public fun position(position: Position?, @IdRes viewId: Int): ConstraintSetBuilder {
        if (position == null) {
            return this
        }

        constrainWithinParent(viewId)

        when (position.horizontal) {
            HorizontalPosition.START -> constraints.setHorizontalBias(viewId, 0.0f)
            HorizontalPosition.END -> constraints.setHorizontalBias(viewId, 1.0f)
            HorizontalPosition.CENTER -> constraints.setHorizontalBias(viewId, 0.5f)
        }

        when (position.vertical) {
            VerticalPosition.TOP -> constraints.setVerticalBias(viewId, 0.0f)
            VerticalPosition.BOTTOM -> constraints.setVerticalBias(viewId, 1.0f)
            VerticalPosition.CENTER -> constraints.setVerticalBias(viewId, 0.5f)
        }

        return this
    }

    public fun margin(margin: Margin?, @IdRes viewId: Int): ConstraintSetBuilder {
        if (margin == null) {
            return this
        }

        constraints.setMargin(
            viewId, ConstraintSet.TOP, ResourceUtils.dpToPx(context, margin.top).toInt()
        )
        constraints.setMargin(
            viewId, ConstraintSet.BOTTOM, ResourceUtils.dpToPx(context, margin.bottom).toInt()
        )
        constraints.setMargin(
            viewId, ConstraintSet.START, ResourceUtils.dpToPx(context, margin.start).toInt()
        )
        constraints.setMargin(
            viewId, ConstraintSet.END, ResourceUtils.dpToPx(context, margin.end).toInt()
        )

        return this
    }

    public fun margin(margin: Margin?, insets: Insets, @IdRes viewId: Int): ConstraintSetBuilder {
        var margin = margin
        if (margin == null) {
            margin = Margin(0, 0, 0, 0)
        }
        constraints.setMargin(
            viewId,
            ConstraintSet.TOP,
            ResourceUtils.dpToPx(context, margin.top).toInt() + insets.top
        )
        constraints.setMargin(
            viewId,
            ConstraintSet.BOTTOM,
            ResourceUtils.dpToPx(context, margin.bottom).toInt() + insets.bottom
        )
        constraints.setMargin(
            viewId,
            ConstraintSet.START,
            ResourceUtils.dpToPx(context, margin.start).toInt() + insets.left
        )
        constraints.setMargin(
            viewId,
            ConstraintSet.END,
            ResourceUtils.dpToPx(context, margin.end).toInt() + insets.right
        )

        return this
    }

    public fun build(): ConstraintSet {
        return constraints
    }

    /** Result of fitting a ratio-locked rect within available bounds. */
    internal data class AspectRatioFit(val widthPx: Int, val heightPx: Int)

    public companion object {
        public fun newBuilder(context: Context): ConstraintSetBuilder {
            return ConstraintSetBuilder(context)
        }

        /**
         * Pure fit computation (no [Context], for unit testing).
         *
         * Returns `null` when the requested rect fits within both available bounds (caller keeps
         * the unchanged ratio path). Otherwise returns the largest box with the given [ratio]
         * (width:height) that fits *both* [availableWidthPx] and [availableHeightPx], as rounded px.
         */
        internal fun computeAspectRatioFit(
            requestedWidthPx: Double,
            requestedHeightPx: Double,
            ratio: Double,
            availableWidthPx: Int,
            availableHeightPx: Int
        ): AspectRatioFit? {
            val fits = requestedWidthPx <= availableWidthPx && requestedHeightPx <= availableHeightPx
            if (fits) {
                return null
            }
            val fitW = minOf(availableWidthPx.toDouble(), availableHeightPx * ratio)
            val fitH = fitW / ratio
            return AspectRatioFit(fitW.roundToInt(), fitH.roundToInt())
        }
    }
}
