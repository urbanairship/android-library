/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.environment.inputData
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.TextInputTextAppearance
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.util.textChanges
import com.urbanairship.android.layout.view.TextInputView
import kotlinx.coroutines.launch

internal class TextInputModel(
    val inputType: FormInputType,
    val textAppearance: TextInputTextAppearance,
    val hintText: String? = null,
    val identifier: String,
    val contentDescription: String? = null,
    val attributeName: AttributeName? = null,
    private val isRequired: Boolean = false,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<TextInputView, TextInputModel.Listener>(
    viewType = ViewType.TEXT_INPUT,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {

    constructor(
        info: TextInputInfo,
        formState: SharedState<State.Form>,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        inputType = info.inputType,
        textAppearance = info.textAppearance,
        attributeName = info.attributeName,
        hintText = info.hintText,
        identifier = info.identifier,
        contentDescription = info.contentDescription,
        isRequired = info.isRequired,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        formState = formState,
        environment = env,
        properties = props
    )

    interface Listener : BaseModel.Listener {
        fun restoreValue(value: String)
    }

    init {
        formState.update { state ->
            state.copyWithFormInput(
                FormData.TextInput(
                    identifier = identifier,
                    value = null,
                    isValid = !isRequired,
                    attributeName = attributeName,
                    attributeValue = null
                )
            )
        }

        modelScope.launch {
            formState.changes.collect { state ->
                listener?.setEnabled(state.isEnabled)
            }
        }
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        TextInputView(context, this).apply {
            id = viewId

            // Restore value, if available
            formState.inputData<FormData.TextInput>(identifier)?.let { input ->
                input.value?.let { listener?.restoreValue(it) }
            }
        }

    override fun onViewCreated(view: TextInputView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.update { state ->
                state.copyWithDisplayState(identifier, isDisplayed)
            }
        }
    }

    override fun onViewAttached(view: TextInputView) {
        // Listen to text changes
        viewScope.launch {
            view.textChanges()
                .collect { value ->
                    formState.update { state ->
                        state.copyWithFormInput(
                            FormData.TextInput(
                                identifier = identifier,
                                value = value,
                                isValid = !isRequired || value.isNotEmpty(),
                                attributeName = attributeName,
                                attributeValue = if (value.isNotEmpty()) { AttributeValue.wrap(value) } else { null }
                            )
                        )
                    }

                    if (eventHandlers.hasFormInputHandler()) {
                        handleViewEvent(EventHandler.Type.FORM_INPUT, value)
                    }
                }
        }

        if (eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }
}
