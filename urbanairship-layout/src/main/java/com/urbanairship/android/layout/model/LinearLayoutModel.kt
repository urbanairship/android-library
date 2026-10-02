/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.view.LinearLayoutView

internal class LinearLayoutModel(
    viewInfo: LinearLayoutInfo,
    val items: List<Item>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<LinearLayoutView, LinearLayoutInfo, BaseModel.Listener>(
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

    /** Linear layouts may be shrunk if they contain any media views. */
    override var isShrinkable: Boolean = items.any { it.model.isShrinkable }

    override fun establishesLength(direction: Direction): Boolean =
        items.any { it.establishesLength(direction) }

    /** Whether any item's order can change with state. */
    val hasItemOverrides: Boolean = items.any { !it.info.overrides?.config.isNullOrEmpty() }

    /**
     * Returns the items in the order they stack and read: [items]' shuffle, then each item's order
     * as [state] resolves it, list order among equal ones.
     *
     * @param state what the items' `overrides` match against: the layout's current state by default,
     * and only read when an item has overrides.
     * @return the ordered items.
     */
    fun orderedItems(state: ThomasState? = if (hasItemOverrides) layoutState.thomasState.value else null): List<Item> =
        items.map { it to (state?.resolveRequired(it.info.overrides?.config, it.info.config) ?: it.info.config).order }
            .sortedBy { (_, order) -> order }
            .map { (item, _) -> item }


    data class Item(
        val info: LinearLayoutItemInfo, val model: AnyModel
    ) {
        internal fun establishesLength(direction: Direction): Boolean {
            val dimension = when (direction) {
                Direction.VERTICAL -> info.size.height
                Direction.HORIZONTAL -> info.size.width
            }
            return when (dimension.type) {
                Size.DimensionType.ABSOLUTE -> true
                Size.DimensionType.PERCENT -> false
                // Auto defers to the content, so the question passes down: a stack of percentages is
                // no more able to supply a length than a percentage is, however deeply it is nested.
                Size.DimensionType.AUTO -> model.establishesLength(direction)
            }
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = LinearLayoutView(context, this, viewEnvironment).apply {
        id = viewId
    }
}
