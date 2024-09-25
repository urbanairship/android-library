/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.launch

/**
 * Controller for checkbox inputs.
 *
 * Must be a descendant of `FormController` or `NpsFormController`.
 */
internal class CheckboxController(
    val view: AnyModel,
    val identifier: String,
    private val isRequired: Boolean = false,
    private val minSelection: Int = if (isRequired) 1 else 0,
    private val maxSelection: Int = Int.MAX_VALUE,
    val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val formState: SharedState<State.Form>,
    private val checkboxState: SharedState<State.Checkbox>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, BaseModel.Listener>(
    viewType = ViewType.CHECKBOX_CONTROLLER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(
        info: CheckboxControllerInfo,
        view: AnyModel,
        formState: SharedState<State.Form>,
        checkboxState: SharedState<State.Checkbox>,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        view = view,
        identifier = info.identifier,
        isRequired = info.isRequired,
        minSelection = info.minSelection,
        maxSelection = info.maxSelection,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        formState = formState,
        checkboxState = checkboxState,
        environment = env,
        properties = props
    )

    init {
        modelScope.launch {
            checkboxState.changes.collect { checkbox ->
                formState.update { form ->
                    form.copyWithFormInput(
                        FormData.CheckboxController(
                            identifier = checkbox.identifier,
                            value = checkbox.selectedItems,
                            isValid = isValid(checkbox.selectedItems)
                        )
                    )
                }

                if (eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, checkbox.selectedItems.toList())
                }
            }
        }

        modelScope.launch {
            formState.changes.collect { form ->
                checkboxState.update { state ->
                    state.copy(isEnabled = form.isEnabled)
                }
            }
        }
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        view.createView(context, viewEnvironment, itemProperties)

    private fun isValid(selectedItems: Set<JsonValue>): Boolean {
        val count = selectedItems.size
        val isFilled = count in minSelection..maxSelection
        val isOptional = count == 0 && !isRequired
        return isFilled || isOptional
    }
}
