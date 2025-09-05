/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.RadioInputInfo
import com.urbanairship.android.layout.property.EventHandler.Type
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.util.checkedChanges
import com.urbanairship.android.layout.view.RadioInputView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

internal class RadioInputModel(
    viewInfo: RadioInputInfo,
    private val radioState: SharedState<State.Radio>,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : CheckableModel<RadioInputView, RadioInputInfo>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = RadioInputView(context, this).apply {
        id = viewId
    }

    override fun onViewCreated(view: RadioInputView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(
                identifier = @OptIn(DelicateLayoutApi::class) radioState.value.identifier,
                isDisplayed = isDisplayed
            )
        }
    }

    override fun onViewAttached(view: RadioInputView) {
        super.onViewAttached(view)

        // Update checked state whenever the selected radio state changes.
        viewScope.launch {
            radioState.changes.collect { state ->
                setChecked(isChecked = state.isSelected(reportingValue = viewInfo.reportingValue))
                setEnabled(isEnabled = state.isEnabled)
            }
        }

        // Share the checkedChanges flow from the view. Since we're starting eagerly, we use a replay
        // of 1 so that we can still collect the initial change and update the form state.
        val checkedChanges =
            view.checkedChanges().shareIn(viewScope, SharingStarted.Eagerly, replay = 1)

        // Update radio controller state when this radio input is selected.
        viewScope.launch {
            checkedChanges.filter { isSelected -> isSelected }.collect {
                    radioState.update { state ->
                        state.copy(
                            selectedItem = viewInfo.asSelected()
                        )
                    }
                }
        }

        // Handle taps if enabled for this view.
        // We drop the first update to ignore the change caused by restoring radio state.
        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                checkedChanges.drop(1).filter { isSelected -> isSelected }
                    .collect { handleViewEvent(Type.TAP) }
            }
        }
    }
}

private fun RadioInputInfo.asSelected(): State.Radio.Selected {
    return State.Radio.Selected(
        identifier = null,
        reportingValue = reportingValue,
        attributeValue = attributeValue
    )
}
