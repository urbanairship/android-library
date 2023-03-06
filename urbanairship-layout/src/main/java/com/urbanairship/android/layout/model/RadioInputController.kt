/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import kotlinx.coroutines.launch

/** Controller for radio inputs. */
internal class RadioInputController(
    val view: AnyModel,
    val identifier: String,
    val isRequired: Boolean = false,
    private val attributeName: AttributeName? = null,
    val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val formState: SharedState<State.Form>,
    private val radioState: SharedState<State.Radio>,
    environment: ModelEnvironment
) : BaseModel<View, BaseModel.Listener>(
    viewType = ViewType.RADIO_INPUT_CONTROLLER,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(
        info: RadioInputControllerInfo,
        view: AnyModel,
        formState: SharedState<State.Form>,
        radioState: SharedState<State.Radio>,
        env: ModelEnvironment
    ) : this(
        view = view,
        identifier = info.identifier,
        isRequired = info.isRequired,
        attributeName = info.attributeName,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        formState = formState,
        radioState = radioState,
        environment = env
    )

    init {
        // Listen to radio input state updates and push them into form state.
        modelScope.launch {
            radioState.changes.collect { radio ->
                formState.update { form ->
                    form.copyWithFormInput(
                        FormData.RadioInputController(
                            identifier = radio.identifier,
                            value = radio.selectedItem,
                            isValid = radio.selectedItem != null || !isRequired,
                            attributeName = attributeName,
                            attributeValue = radio.attributeValue
                        )
                    )
                }

                if (eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, radio.selectedItem)
                }
            }
        }

        modelScope.launch {
            formState.changes.collect { form ->
                radioState.update { it.copy(isEnabled = form.isEnabled) }
            }
        }
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        view.createView(context, viewEnvironment)
}
