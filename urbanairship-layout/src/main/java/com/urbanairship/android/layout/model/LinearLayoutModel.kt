/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.ViewType

internal class LinearLayoutModel(
    items: List<Item>,
    val direction: Direction,
    val randomizeChildren: Boolean = false,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : LayoutModel<LinearLayoutInfo>(ViewType.LINEAR_LAYOUT, backgroundColor, border, environment) {

    constructor(info: LinearLayoutInfo, env: ModelEnvironment) : this(
        items = info.items.map {
            Item(info = it, model = env.modelProvider.create(it.info, env))
        },
        direction = info.direction,
        randomizeChildren = info.randomizeChildren,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    val items = items.apply {
        if (randomizeChildren) { shuffled() }
    }

    final override val children: List<BaseModel> = items.map { it.model }

    init {
        for (c in children) {
            c.addListener(this)
        }
    }

    data class Item(
        val info: LinearLayoutItemInfo,
        val model: BaseModel
    )
}
