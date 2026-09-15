/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.StackImageViewInfo
import com.urbanairship.android.layout.view.StackImageView

internal class StackImageViewModel(
    viewInfo: StackImageViewInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<StackImageView, StackImageViewInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = StackImageView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }
}
