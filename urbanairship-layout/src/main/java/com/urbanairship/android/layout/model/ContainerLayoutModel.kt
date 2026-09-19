/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.ContainerLayoutItemInfo
import com.urbanairship.android.layout.view.ContainerLayoutView

internal class ContainerLayoutModel(
    viewInfo: ContainerLayoutInfo,
    val items: List<Item>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<ContainerLayoutView, ContainerLayoutInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    /** Container layouts may be shrunk if they contain any media views. */
    override var isShrinkable: Boolean = items.any { it.model.isShrinkable }

    override fun establishesLength(direction: Direction): Boolean =
        items.any { it.establishesLength(direction) }


    data class Item(
        val info: ContainerLayoutItemInfo,
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

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        ContainerLayoutView(context, this, viewEnvironment, itemProperties).apply {
            id = viewId
        }
}
