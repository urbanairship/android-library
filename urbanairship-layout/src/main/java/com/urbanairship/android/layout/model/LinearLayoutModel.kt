/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.environment.ModelEnvironment
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

    /** Linear layouts may be shrunk if they contain any media views. */
    override var isShrinkable: Boolean = items.any { it.model.isShrinkable }

    override fun establishesLength(direction: Direction): Boolean =
        items.any { it.establishesLength(direction) }


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
