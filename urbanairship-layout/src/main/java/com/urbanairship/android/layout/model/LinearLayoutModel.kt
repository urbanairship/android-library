/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.LinearLayoutView

internal class LinearLayoutModel(
    val items: List<Item>,
    val direction: Direction,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel<LinearLayoutView, BaseModel.Listener>(
    viewType = ViewType.LINEAR_LAYOUT,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(info: LinearLayoutInfo, items: List<Item>, env: ModelEnvironment) : this(
        items = items,
        direction = info.direction,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )

    data class Item(
        val info: LinearLayoutItemInfo,
        val model: AnyModel
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        LinearLayoutView(context, this, viewEnvironment).apply {
            id = viewId
        }
}
