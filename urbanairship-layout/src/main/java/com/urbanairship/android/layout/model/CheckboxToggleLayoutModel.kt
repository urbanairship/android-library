package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.CheckboxToggleLayoutInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.view.ToggleLayoutView
import kotlinx.coroutines.launch

internal class CheckboxToggleLayoutModel(
    viewInfo: CheckboxToggleLayoutInfo,
    view: AnyModel,
    private val checkboxState: SharedState<State.Checkbox>,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseToggleLayoutModel<ToggleLayoutView<CheckboxToggleLayoutModel>, CheckboxToggleLayoutInfo>(viewInfo, view, formState, environment, properties) {

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ToggleLayoutView(context, this, viewEnvironment, itemProperties, ToggleLayoutView.ToggleLayoutType.CHECKBOX).apply {
        id = viewId
    }

    override fun onViewCreated(view: ToggleLayoutView<CheckboxToggleLayoutModel>) {
        super.onViewCreated(view)
        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(
                identifier = @OptIn(DelicateLayoutApi::class) checkboxState.value.identifier,
                isDisplayed = isDisplayed
            )
        }
    }

    override fun onViewAttached(view: ToggleLayoutView<CheckboxToggleLayoutModel>) {
        super.onViewAttached(view)
        // Update checked state whenever the selection state changes.
        viewScope.launch {
            checkboxState.changes.collect { state ->
                val isChecked = state.isSelected(viewInfo.identifier)
                setChecked(isChecked = isChecked)
                listener?.setEnabled(state.isEnabled && (state.selectedItems.size < state.maxSelection || isChecked))
            }
        }

        // Update form state on every checked change.
        viewScope.launch {
            isOn.collect { isOn ->
                checkboxState.update { state ->
                    state.copy(
                        selectedItems = if (isOn) {
                            state.selectedItems + viewInfo.asSelected()
                        } else {
                            state.selectedItems - viewInfo.asSelected()
                        }
                    )
                }

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, isOn)
                }
            }
        }
    }
}

private fun CheckboxToggleLayoutInfo.asSelected(): State.Checkbox.Selected {
    return State.Checkbox.Selected(
        identifier = identifier,
        reportingValue = reportingValue
    )
}
