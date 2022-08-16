/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.ScrollLayoutInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.ViewType

internal class ScrollLayoutModel(
    final val view: BaseModel,
    val direction: Direction = Direction.VERTICAL,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : LayoutModel<ScrollLayoutInfo>(
    viewType = ViewType.SCROLL_LAYOUT,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: ScrollLayoutInfo, env: ModelEnvironment) : this(
        view = env.modelProvider.create(info.view, env),
        direction = info.direction,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    override val children: List<BaseModel> = listOf(view)

    init {
        view.addListener(this)
    }
}
