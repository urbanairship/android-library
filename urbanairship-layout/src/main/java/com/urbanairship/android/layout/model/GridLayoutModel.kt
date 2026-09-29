/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.GridLayoutEdgeInfo
import com.urbanairship.android.layout.info.GridLayoutInfo
import com.urbanairship.android.layout.info.GridLayoutItemInfo
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.everyConfigFitsAMinimum
import com.urbanairship.android.layout.property.everyConfigHasRatio
import com.urbanairship.android.layout.view.GridLayoutView

internal class GridLayoutModel(
    viewInfo: GridLayoutInfo,
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

    /** Ratio cells take their height from the grid's width, but only every config setting one makes that certain. */
    override fun establishesLength(direction: Direction): Boolean {
        if (direction == Direction.VERTICAL && viewInfo.config.everyConfigHasRatio) {
            return true
        }
        if (direction == Direction.HORIZONTAL && viewInfo.config.everyConfigFitsAMinimum) {
            return true
        }
        return items.any { it.establishesLength(direction) }
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
