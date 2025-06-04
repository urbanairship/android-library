package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ScoreToggleLayoutInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.view.ToggleLayoutView
import kotlinx.coroutines.launch

internal class ScoreInputToggleLayoutModel(
    viewInfo: ScoreToggleLayoutInfo,
    view: AnyModel,
    private val scoreState: SharedState<State.Score>,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseToggleLayoutModel<ToggleLayoutView<ScoreInputToggleLayoutModel>, ScoreToggleLayoutInfo>(viewInfo, view, formState, environment, properties) {

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ToggleLayoutView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }

    override fun onViewCreated(view: ToggleLayoutView<ScoreInputToggleLayoutModel>) {
        super.onViewCreated(view)
        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(
                identifier = scoreState.changes.value.identifier,
                isDisplayed = isDisplayed
            )
        }
    }

    override fun onViewAttached(view: ToggleLayoutView<ScoreInputToggleLayoutModel>) {
        super.onViewAttached(view)
        // Update checked state whenever the selection state changes.
        viewScope.launch {
            scoreState.changes.collect { state ->
                setChecked(isChecked = state.isSelected(identifier = viewInfo.identifier))
                listener?.setEnabled(state.isEnabled)
            }
        }

        // Update form state on every checked change.
        viewScope.launch {
            isOn.collect { isOn ->
                if (isOn) {
                    scoreState.update { state ->
                        state.copy(
                            selectedItem = viewInfo.asSelected()
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

private fun ScoreToggleLayoutInfo.asSelected(): State.Score.Selected = State.Score.Selected(
    identifier = identifier,
    reportingValue = reportingValue,
    attributeValue = attributeValue
)
