/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.EmptyInfo
import com.urbanairship.android.layout.view.EmptyView

/**
 * An empty view that can have a background and border.
 * Useful for the nub on a banner or as dividers between items.
 *
 * @see EmptyView
 */
internal class EmptyModel(
    viewInfo: EmptyInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<EmptyView, EmptyInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = EmptyView(context, this).apply {
        id = viewId
    }
}
