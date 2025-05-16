/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ScrollLayoutInfo
import com.urbanairship.android.layout.view.VerticalScrollLayoutView

internal class VerticalScrollLayoutModel(
    viewInfo: ScrollLayoutInfo,
    val view: AnyModel,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<VerticalScrollLayoutView, ScrollLayoutInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = VerticalScrollLayoutView(context, this, viewEnvironment).apply {
        id = viewId
    }
}
