package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.RadioInputToggleLayoutInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.view.ToggleLayoutView
import kotlinx.coroutines.launch

internal class RadioInputToggleLayoutModel(
    viewInfo: RadioInputToggleLayoutInfo,
    view: AnyModel,
    private val radioState: SharedState<State.Radio>,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseToggleLayoutModel<ToggleLayoutView<RadioInputToggleLayoutModel>, RadioInputToggleLayoutInfo>(viewInfo, view, formState, environment, properties) {

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ToggleLayoutView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }

    override fun onViewCreated(view: ToggleLayoutView<RadioInputToggleLayoutModel>) {
        super.onViewCreated(view)
        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(
                identifier = @OptIn(DelicateLayoutApi::class) radioState.value.identifier,
                isDisplayed = isDisplayed
            )
        }
    }

    override fun onViewAttached(view: ToggleLayoutView<RadioInputToggleLayoutModel>) {
        super.onViewAttached(view)
        // Update checked state whenever the selection state changes.
        viewScope.launch {
            radioState.changes.collect { state ->
                setChecked(isChecked = state.selectedItem == viewInfo.reportingValue)
                listener?.setEnabled(state.isEnabled)
            }
        }

        // Update form state on every checked change.
        viewScope.launch {
            isOn.collect { isOn ->
                if (isOn) {
                    radioState.update { state ->
                        state.copy(
                            selectedItem = viewInfo.reportingValue,
                            attributeValue = viewInfo.attributeValue
                        )
                    }
                }

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, isOn)
                }
            }
        }
    }
}
