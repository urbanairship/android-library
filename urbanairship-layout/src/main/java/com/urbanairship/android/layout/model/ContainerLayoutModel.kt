/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.ContainerItemInfo
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType

internal class ContainerLayoutModel(
    final val items: List<Item>,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : LayoutModel<ContainerLayoutInfo>(
    viewType = ViewType.CONTAINER,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {

    constructor(info: ContainerLayoutInfo, env: ModelEnvironment) : this(
        items = info.items.map {
            Item(info = it, model = env.modelProvider.create(it.info, env))
        },
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    final override val children: List<BaseModel> = items.map { it.model }

    data class Item(
        val info: ContainerItemInfo,
        val model: BaseModel
    )

    init {
        for (c in children) {
            c.addListener(this)
        }
    }
}
