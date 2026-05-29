/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.view.ImageButtonView

internal class ImageButtonModel(
    viewInfo: ImageButtonInfo,
    formState: ThomasForm?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : ButtonModel<ImageButtonView, ImageButtonInfo>(
    viewInfo = viewInfo,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {

    val buttonViewId: Int = View.generateViewId()

    fun resolveImage(state: ThomasState?): Image =
        state?.resolveRequired(
            overrides = viewInfo.viewOverrides?.image,
            default = viewInfo.image
        ) ?: viewInfo.image

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ImageButtonView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }
}
