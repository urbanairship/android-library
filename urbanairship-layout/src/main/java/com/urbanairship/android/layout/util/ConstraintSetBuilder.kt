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
        return this
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

    public companion object {
        public fun newBuilder(context: Context): ConstraintSetBuilder {
            return ConstraintSetBuilder(context)
        }
    }
}
