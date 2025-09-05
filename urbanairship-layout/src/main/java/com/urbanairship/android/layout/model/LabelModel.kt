/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.view.LabelView

internal class LabelModel(
    viewInfo: LabelInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<LabelView, LabelInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = LabelView(context, this).apply {
        id = viewId

        viewInfo.labels?.let { label ->
            if (label.type == LabelInfo.AssociatedLabel.Type.LABELS) {
                val id = environment.viewIdResolver.viewId(label.viewId, label.viewType)
                labelFor = id
            }
        }
    }
}
