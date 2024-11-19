/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ToggleInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.EventHandler.Type
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.util.checkedChanges
import com.urbanairship.android.layout.view.ToggleView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Toggle input for use within a `FormController` or `NpsFormController`.
 */
internal class ToggleModel(
    viewInfo: ToggleInfo,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : CheckableModel<ToggleView, ToggleInfo>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = ToggleView(context, this).apply {
        id = viewId
    }

    override fun onViewCreated(view: ToggleView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.update { state ->
                state.copyWithDisplayState(viewInfo.identifier, isDisplayed)
            }
        }
    }

    override fun onViewAttached(view: ToggleView) {
        // Share the checkedChanges flow from the view. Since we're starting eagerly, we use a replay
        // of 1 so that we can still collect the initial change and update the form state.
        val checkedChanges =
            view.checkedChanges().shareIn(viewScope, SharingStarted.Eagerly, replay = 1)

        // Update form state on every checked change.
        viewScope.launch {
            checkedChanges.collect { isChecked ->
                formState.update { state ->
                    state.copyWithFormInput(
                        FormData.Toggle(
                            identifier = viewInfo.identifier,
                            value = isChecked,
                            isValid = isChecked || !viewInfo.isRequired,
                            attributeName = viewInfo.attributeName,
                            attributeValue = viewInfo.attributeValue
                        )
                    )
                }

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, isChecked)
                }
            }
        }

        // Handle taps if enabled for this view.
        // We drop the first update to ignore the change caused by restoring toggle state.
        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                checkedChanges.drop(1).collect { handleViewEvent(Type.TAP) }
            }
        }

        viewScope.launch {
            formState.changes.collect { state -> setEnabled(state.isEnabled) }
        }
    }
}
