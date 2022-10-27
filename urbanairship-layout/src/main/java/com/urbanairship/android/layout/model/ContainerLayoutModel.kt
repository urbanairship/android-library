/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.ContainerItemInfo
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType

internal class ContainerLayoutModel(
    val items: List<Item>,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : LayoutModel(
    viewType = ViewType.CONTAINER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {

    constructor(info: ContainerLayoutInfo, env: ModelEnvironment) : this(
        items = info.items.map {
            Item(info = it, model = env.modelProvider.create(it.info, env))
        },
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
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
