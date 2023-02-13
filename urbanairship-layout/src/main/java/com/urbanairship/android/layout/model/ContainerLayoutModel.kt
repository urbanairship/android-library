/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.ContainerLayoutItemInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.ContainerLayoutView

internal class ContainerLayoutModel(
    val items: List<Item>,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel<ContainerLayoutView, BaseModel.Listener>(
    viewType = ViewType.CONTAINER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {

    constructor(info: ContainerLayoutInfo, items: List<Item>, env: ModelEnvironment) : this(
        items = items,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )

    data class Item(
        val info: ContainerLayoutItemInfo,
        val model: AnyModel
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        ContainerLayoutView(context, this, viewEnvironment).apply {
            id = viewId
        }
}
