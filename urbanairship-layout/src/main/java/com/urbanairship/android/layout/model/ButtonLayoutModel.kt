/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ButtonLayoutInfo
import com.urbanairship.android.layout.util.resolveContentDescription
import com.urbanairship.android.layout.view.ButtonLayoutView

internal class ButtonLayoutModel(
    viewInfo: ButtonLayoutInfo,
    val view: AnyModel,
    formState: ThomasForm?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties,
) : ButtonModel<ButtonLayoutView, ButtonLayoutInfo>(
    viewInfo = viewInfo,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties,
) {

    /** See [videoBackgroundModels] -- a playable layer is a real child view, not painted. */
    val videoBackgroundModels = videoBackgroundModels(
        layers = viewInfo.backgrounds,
        pagerState = environment.layoutState.pager,
        environment = environment,
        properties = properties
    )

    /** Wraps a view without resizing it, so what it wraps answers for it. */
    override fun establishesLength(direction: Direction): Boolean =
        view.establishesLength(direction)

    override var isShrinkable: Boolean = view.isShrinkable

    override fun contentDescription(context: Context): String? {
        return context.resolveContentDescription(viewInfo.contentDescription, viewInfo.localizedContentDescription)
    }

    override fun reportingDescription(context: Context): String {
        return viewInfo.reportingDescriptionResolver(context)
    }

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ButtonLayoutView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }
}

private val ButtonLayoutInfo.reportingDescriptionResolver: (Context) -> String
    get() = { context ->
        context.resolveContentDescription(
            contentDescription, localizedContentDescription
        ) ?: identifier
    }
