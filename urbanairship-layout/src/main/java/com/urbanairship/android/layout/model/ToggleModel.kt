/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ToggleInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.EventHandler.Type
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.AttributeName
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
    val identifier: String,
    toggleStyle: ToggleStyle,
    private val isRequired: Boolean = false,
    private val attributeName: AttributeName? = null,
    private val attributeValue: AttributeValue? = null,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment
) : CheckableModel<ToggleView>(
    viewType = ViewType.TOGGLE,
    style = toggleStyle,
    toggleType = toggleStyle.type,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {

    constructor(
        info: ToggleInfo,
        formState: SharedState<State.Form>,
        env: ModelEnvironment
    ) : this(
        identifier = info.identifier,
        toggleStyle = info.style,
        isRequired = info.isRequired,
        attributeName = info.attributeName,
        attributeValue = info.attributeValue,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        formState = formState,
        environment = env
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        ToggleView(context, this).apply {
            id = viewId
        }

    override fun onViewAttached(view: ToggleView) {
        // Share the checkedChanges flow from the view. Since we're starting eagerly, we use a replay
        // of 1 so that we can still collect the initial change and update the form state.
        val checkedChanges = view.checkedChanges()
            .shareIn(viewScope, SharingStarted.Eagerly, replay = 1)

        // Update form state on every checked change.
        viewScope.launch {
            checkedChanges.collect { isChecked ->
                formState.update { state ->
                    state.copyWithFormInput(
                        FormData.Toggle(
                            identifier = identifier,
                            value = isChecked,
                            isValid = isChecked || !isRequired,
                            attributeName = attributeName,
                            attributeValue = attributeValue
                        )
                    )
                }

                if (eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, isChecked)
                }
            }
        }

        // Handle taps if enabled for this view.
        // We drop the first update to ignore the change caused by restoring toggle state.
        if (eventHandlers.hasTapHandler()) {
            viewScope.launch {
                checkedChanges.drop(1)
                    .collect { handleViewEvent(Type.TAP) }
            }
        }

        viewScope.launch {
            formState.changes.collect { state -> setEnabled(state.isEnabled) }
        }
    }
}
