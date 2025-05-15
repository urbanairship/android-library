/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ScrollLayoutInfo
import com.urbanairship.android.layout.view.HorizontalScrollLayoutView

internal class HorizontalScrollLayoutModel(
    viewInfo: ScrollLayoutInfo,
    val view: AnyModel,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<HorizontalScrollLayoutView, ScrollLayoutInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = HorizontalScrollLayoutView(context, this, viewEnvironment).apply {
        id = viewId
    }
}
