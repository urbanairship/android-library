/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.view.LinearLayoutView

internal class LinearLayoutModel(
    viewInfo: LinearLayoutInfo,
    val items: List<Item>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<LinearLayoutView, LinearLayoutInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    /** Linear layouts may be shrunk if they contain any media views. */
    override var isShrinkable: Boolean = items.any { it.model.isShrinkable }

    data class Item(
        val info: LinearLayoutItemInfo, val model: AnyModel
    )

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = LinearLayoutView(context, this, viewEnvironment).apply {
        id = viewId
    }
}
