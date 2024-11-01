/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.CheckboxInfo
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.util.checkedChanges
import com.urbanairship.android.layout.view.CheckboxView
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Checkbox input for use within a `CheckboxController`.
 */
internal class CheckboxModel(
    toggleStyle: ToggleStyle,
    private val reportingValue: JsonValue,
    contentDescription: String? = null,
    localizedContentDescription: LocalizedContentDescription? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val checkboxState: SharedState<State.Checkbox>,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : CheckableModel<CheckboxView>(
    viewType = ViewType.CHECKBOX,
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
        info: CheckboxInfo,
        checkboxState: SharedState<State.Checkbox>,
        formState: SharedState<State.Form>,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        toggleStyle = info.style,
        reportingValue = info.reportingValue,
        contentDescription = info.contentDescription,
        localizedContentDescription = info.localizedContentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        checkboxState = checkboxState,
        formState = formState,
        environment = env,
        properties = props
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        CheckboxView(context, this).apply {
            id = viewId
        }

    override fun onViewCreated(view: CheckboxView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.update { state ->
                @OptIn(DelicateLayoutApi::class)
                val identifier = checkboxState.value.identifier
                state.copyWithDisplayState(identifier, isDisplayed)
            }
        }
    }

    override fun onViewAttached(view: CheckboxView) {
        // Update checked state whenever the selection state changes.
        viewScope.launch {
            checkboxState.changes.collect { state ->
                val isChecked = state.selectedItems.contains(reportingValue)
                setChecked(isChecked = isChecked)
                setEnabled(isEnabled = state.isEnabled && (state.selectedItems.size < state.maxSelection || isChecked))
            }
        }

        // Share the checkedChanges flow from the view. Since we're starting eagerly, we use a replay
        // of 1 so that we can still collect the initial change and update the form state.
        val checkedChanges = view.checkedChanges()
            .shareIn(viewScope, SharingStarted.Eagerly, replay = 1)

        // Update checkbox controller whenever this checkbox is checked/unchecked.
        viewScope.launch {
            checkedChanges.collect { isChecked ->
                checkboxState.update { state ->
                    state.copy(
                        selectedItems = if (isChecked) {
                            state.selectedItems + reportingValue
                        } else {
                            state.selectedItems - reportingValue
                        }
                    )
                }
            }
        }

        // Handle taps if enabled for this view.
        // We drop the first update to ignore the change caused by restoring checkbox state.
        if (eventHandlers.hasTapHandler()) {
            viewScope.launch {
                checkedChanges
                    .drop(1)
                    .collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }
}
