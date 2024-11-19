/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
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
    data class Item(
        val info: ContainerLayoutItemInfo,
        val model: AnyModel
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        ContainerLayoutView(context, this, viewEnvironment).apply {
            id = viewId
        }
}
