/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.android.layout.info.RadioInputInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.EventHandler.Type
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.util.checkedChanges
import com.urbanairship.android.layout.view.RadioInputView
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

internal class RadioInputModel(
    toggleStyle: ToggleStyle,
    private val reportingValue: JsonValue,
    private val attributeValue: AttributeValue? = null,
    contentDescription: String? = null,
    localizedContentDescription: LocalizedContentDescription? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val radioState: SharedState<State.Radio>,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : CheckableModel<RadioInputView>(
    viewType = ViewType.RADIO_INPUT,
    style = toggleStyle,
    toggleType = toggleStyle.type,
    contentDescription = contentDescription,
    localizedContentDescription = localizedContentDescription,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(
        info: RadioInputInfo,
        radioState: SharedState<State.Radio>,
        formState: SharedState<State.Form>,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        toggleStyle = info.style,
        reportingValue = info.reportingValue,
        attributeValue = info.attributeValue,
        contentDescription = info.contentDescription,
        localizedContentDescription = info.localizedContentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        radioState = radioState,
        formState = formState,
        environment = env,
        properties = props
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        RadioInputView(context, this).apply {
            id = viewId
        }

    override fun onViewCreated(view: RadioInputView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.update { state ->
                @OptIn(DelicateLayoutApi::class)
                val identifier = radioState.value.identifier
                state.copyWithDisplayState(identifier, isDisplayed)
            }
        }
    }

    override fun onViewAttached(view: RadioInputView) {
        // Update checked state whenever the selected radio state changes.
        viewScope.launch {
            radioState.changes.collect { state ->
                setChecked(isChecked = state.selectedItem == reportingValue)
                setEnabled(isEnabled = state.isEnabled)
            }
        }

        // Share the checkedChanges flow from the view. Since we're starting eagerly, we use a replay
        // of 1 so that we can still collect the initial change and update the form state.
        val checkedChanges = view.checkedChanges()
            .shareIn(viewScope, SharingStarted.Eagerly, replay = 1)

        // Update radio controller state when this radio input is selected.
        viewScope.launch {
            checkedChanges
                .filter { isSelected -> isSelected }
                .collect {
                    radioState.update { state ->
                        state.copy(
                            selectedItem = reportingValue,
                            attributeValue = attributeValue
                        )
                    }
            }
        }

        // Handle taps if enabled for this view.
        // We drop the first update to ignore the change caused by restoring radio state.
        if (eventHandlers.hasTapHandler()) {
            viewScope.launch {
                checkedChanges.drop(1)
                    .filter { isSelected -> isSelected }
                    .collect { handleViewEvent(Type.TAP) }
            }
        }
    }
}
