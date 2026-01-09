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
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.android.layout.info.StackImageButtonInfo
import com.urbanairship.android.layout.info.StackItemInfo
import com.urbanairship.android.layout.util.resolveContentDescription
import com.urbanairship.android.layout.view.StackImageButtonView

internal class StackImageButtonModel(
    viewInfo: StackImageButtonInfo,
    formState: ThomasForm?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : ButtonModel<StackImageButtonView, StackImageButtonInfo>(
    viewInfo = viewInfo,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {

    val buttonViewId: Int = View.generateViewId()

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = StackImageButtonView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }

    fun resolveState(context: Context, state: ThomasState?): ResolvedState {
        return if (state != null) {
            ResolvedState(
                items = state.resolveOptional(
                    overrides = viewInfo.viewOverrides?.overrides,
                    default = viewInfo.items
                ),
                contentDescription = state.resolveOptional(
                    overrides = viewInfo.viewOverrides?.contentDescription,
                    default = viewInfo.contentDescription
                ),
                localizedContentDescription = state.resolveOptional(
                    overrides = viewInfo.viewOverrides?.localizedContentDescription,
                    default = viewInfo.localizedContentDescription
                )
            )
        } else {
            ResolvedState(
                items = viewInfo.items,
                contentDescription = viewInfo.contentDescription,
                localizedContentDescription = viewInfo.localizedContentDescription
            )
        }
    }

    fun resolveContentDescription(context: Context, state: ThomasState?): String? {
        val resolved = resolveState(context, state)

        // First try resolved contentDescription
        resolved.contentDescription?.let { return it }

        // Then try resolved localizedContentDescription
        return context.resolveContentDescription(
            contentDescription = null,
            localizedContentDescription = resolved.localizedContentDescription
        )
    }

    data class ResolvedState(
        val items: List<StackItemInfo>?,
        val contentDescription: String? = null,
        val localizedContentDescription: LocalizedContentDescription? = null
    )
}
