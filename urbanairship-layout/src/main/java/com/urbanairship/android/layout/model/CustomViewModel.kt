/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.AirshipCustomViewArguments
import com.urbanairship.android.layout.AirshipCustomViewArguments.SizeInfo
import com.urbanairship.android.layout.AirshipCustomViewHandler
import com.urbanairship.android.layout.AirshipCustomViewManager
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.CustomViewInfo
import com.urbanairship.android.layout.view.CustomView

/** Model for customer provided views. */
internal class CustomViewModel(
    viewInfo: CustomViewInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<CustomView, CustomViewInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    private var itemProperties: ItemProperties? = null

    private val handler: AirshipCustomViewHandler?
        get() = AirshipCustomViewManager.get(viewInfo.name)

    fun tryInflateView(context: Context): View? {
        val args = AirshipCustomViewArguments(
            name = viewInfo.name,
            properties = viewInfo.properties,
            sizeInfo = SizeInfo(
                isAutoWidth = itemProperties?.size?.width?.isAuto ?: false,
                isAutoHeight = itemProperties?.size?.height?.isAuto ?: false,
            )
        )

        return handler?.onCreateView(context, args)
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ): CustomView {
        this.itemProperties = itemProperties

        return CustomView(context, this).apply {
            id = viewId
        }
    }
}
