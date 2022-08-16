/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.EmptyInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.EmptyView

/**
 * An empty view that can have a background and border.
 * Useful for the nub on a banner or as dividers between items.
 *
 * @see EmptyView
 */
internal class EmptyModel(
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(
    viewType = ViewType.EMPTY_VIEW,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: EmptyInfo, env: ModelEnvironment) : this(
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )
}
