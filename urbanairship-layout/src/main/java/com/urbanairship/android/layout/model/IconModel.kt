package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.IconViewInfo
import com.urbanairship.android.layout.view.IconView

internal class IconModel(
    viewInfo: IconViewInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
): BaseModel<IconView, IconViewInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = IconView(context, this).apply {
        id = viewId
    }
}
