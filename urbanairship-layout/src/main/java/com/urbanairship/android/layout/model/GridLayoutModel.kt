/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.GridState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.GridLayoutEdgeInfo
import com.urbanairship.android.layout.info.GridLayoutInfo
import com.urbanairship.android.layout.info.GridLayoutItemInfo
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.GridColumns
import com.urbanairship.android.layout.view.GridLayoutView

internal class GridLayoutModel(
    viewInfo: GridLayoutInfo,
    /** This grid's `$grids.current`, which the grid and everything inside it see. */
    val gridState: SharedState<GridState>,
    val items: List<Item>,
    val header: Edge? = null,
    val footer: Edge? = null,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<GridLayoutView, GridLayoutInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    /** See [videoBackgroundModels] -- a playable layer is a real child view, not painted. */
    val videoBackgroundModels = videoBackgroundModels(
        layers = viewInfo.backgrounds,
        pagerState = environment.layoutState.pager,
        environment = environment,
        properties = properties
    )

    override var isShrinkable: Boolean = items.any { it.model.isShrinkable }

    /** Whether the grid or any item has an override to re-resolve when the state changes. */
    val hasOverrides: Boolean = !viewInfo.viewOverrides?.config.isNullOrEmpty() ||
        items.any { !it.info.overrides?.config.isNullOrEmpty() }

    /** Ratio cells take their height from the grid's width, but only every config setting one makes that certain. */
    override fun establishesLength(direction: Direction): Boolean {
        if (direction == Direction.VERTICAL && everyConfigHasRatio) {
            return true
        }
        if (direction == Direction.HORIZONTAL && everyConfigFitsAMinimum) {
            return true
        }
        return items.any { it.establishesLength(direction) }
    }

    /** Whether the grid sets its row heights from a ratio whichever config it lays out with. */
    val everyConfigHasRatio: Boolean = viewInfo.allConfigs.all { it.cellAspectRatio != null }

    /**
     * Whether every config has `fit` columns with a minimum, so a grid with no width to divide is
     * `n × min + gaps` wide from its config alone, whichever config it lays out with.
     */
    private val everyConfigFitsAMinimum: Boolean = viewInfo.allConfigs.all {
        (it.columns as? GridColumns.Fit)?.minWidth?.size?.let { size -> size > 0 } == true
    }

    data class Item(
        val info: GridLayoutItemInfo,
        val model: AnyModel
    ) {
        internal fun establishesLength(direction: Direction): Boolean {
            val dimension = when (direction) {
                Direction.VERTICAL -> info.size.height
                Direction.HORIZONTAL -> info.size.width
            }
            return when (dimension.type) {
                Size.DimensionType.ABSOLUTE -> true
                Size.DimensionType.PERCENT -> false
                Size.DimensionType.AUTO -> model.establishesLength(direction)
            }
        }
    }

    /** The header or footer, and its model. */
    data class Edge(
        val info: GridLayoutEdgeInfo,
        val model: AnyModel
    )

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = GridLayoutView(context, this, viewEnvironment).apply {
        id = viewId
    }
}
