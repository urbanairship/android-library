package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.property.Direction
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.StateControllerInfo

internal class StateController(
    viewInfo: StateControllerInfo,
    val view: AnyModel,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, StateControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    /** Wraps a view without resizing it, so what it wraps answers for it. */
    override fun establishesLength(direction: Direction): Boolean =
        view.establishesLength(direction)

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ): View {
        viewInfo.initialState?.let { state ->
            environment.layoutState.layout.update { it.copyWithState(state = state) }
        }

        return view.createView(context, viewEnvironment, itemProperties)
    }
}
