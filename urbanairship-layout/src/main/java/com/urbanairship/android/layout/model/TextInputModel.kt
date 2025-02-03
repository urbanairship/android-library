/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.environment.inputData
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.util.onEditing
import com.urbanairship.android.layout.util.textChanges
import com.urbanairship.android.layout.view.TextInputView
import com.urbanairship.util.airshipIsValidEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class TextInputModel(
    viewInfo: TextInputInfo,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<TextInputView, TextInputInfo, TextInputModel.Listener>(
    viewInfo = viewInfo, environment = environment, properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun restoreValue(value: String)
    }

    init {
        formState.update { state ->
            state.copyWithFormInput(
                FormData.TextInput(
                    textInput = viewInfo.inputType,
                    identifier = viewInfo.identifier,
                    value = null,
                    isValid = !viewInfo.isRequired,
                    attributeName = viewInfo.attributeName,
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

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = TextInputView(context, this).apply {
        id = viewId

        // Restore value, if available
        formState.inputData<FormData.TextInput>(viewInfo.identifier)?.let { input ->
            input.value?.let { listener?.restoreValue(it) }
        }
    }

    override fun onViewCreated(view: TextInputView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.update { state ->
                state.copyWithDisplayState(viewInfo.identifier, isDisplayed)
            }
        }
    }

    // Leaving this as suspend for now for SMS validation in the future
    fun validate(text: String?): Boolean {
        if (text.isNullOrEmpty()) {
            return !viewInfo.isRequired
        }

        return when (viewInfo.inputType) {
            FormInputType.EMAIL -> {
                return text.airshipIsValidEmail()
            }
            FormInputType.NUMBER -> true
            FormInputType.TEXT -> true
            FormInputType.TEXT_MULTILINE -> true
        }
    }

    private fun channelRegistration(address: String?): ThomasChannelRegistration? {
        if (address.isNullOrEmpty()) {
            return null
        }

        return when (viewInfo.inputType) {
            FormInputType.EMAIL -> {
                viewInfo.emailRegistrationOptions?.let {
                    ThomasChannelRegistration.Email(address, it)
                }
            }
            FormInputType.NUMBER -> null
            FormInputType.TEXT -> null
            FormInputType.TEXT_MULTILINE -> null
        }
    }

    enum class ValidationState {
        VALIDATING, VALID, INVALID
    }

    enum class ValidationAction {
        EDIT, VALID, ERROR
    }

    override fun onViewAttached(view: TextInputView) {
        // Listen to text changes
        val validationState = MutableStateFlow<ValidationState?>(null)

        viewScope.launch {
            view.textChanges().collect { value ->
                val updateValidationState = value.isNotEmpty() || validationState.value != null
                if (updateValidationState) {
                    validationState.update { ValidationState.VALIDATING }
                }

                val trimmed = value.trim()
                val isValid = validate(trimmed)

                if (updateValidationState) {
                    validationState.update { if (isValid) ValidationState.VALID else ValidationState.INVALID  }
                }

                formState.update { state ->
                    state.copyWithFormInput(
                        FormData.TextInput(
                            textInput = viewInfo.inputType,
                            identifier = viewInfo.identifier,
                            value = value,
                            isValid = isValid,
                            attributeName = viewInfo.attributeName,
                            attributeValue = if (trimmed.isNotEmpty()) {
                                AttributeValue.wrap(trimmed)
                            } else {
                                null
                            },
                            channelRegistration = channelRegistration(trimmed)
                        )
                    )
                }
            }
        }

        viewScope.launch {
            combine(view.onEditing(), validationState) { isEditing, validationState ->
                if (validationState == null) {
                    return@combine null
                }

                if (isEditing) {
                    return@combine ValidationAction.EDIT
                }

                return@combine when (validationState) {
                    ValidationState.VALIDATING -> null
                    ValidationState.VALID ->  ValidationAction.VALID
                    ValidationState.INVALID -> ValidationAction.ERROR
                }
            }
                .distinctUntilChanged()
                .mapNotNull {
                    when(it) {
                        ValidationAction.EDIT -> viewInfo.onEdit
                        ValidationAction.VALID -> viewInfo.onValid
                        ValidationAction.ERROR -> viewInfo.onError
                        null -> null
                    }
                }
                .collect {
                    runStateActions(it.actions, view.text)
                }
        }

        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }
}
