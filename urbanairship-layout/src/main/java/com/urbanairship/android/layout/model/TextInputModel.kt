/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.environment.inputData
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.util.textChanges
import com.urbanairship.android.layout.view.TextInputView
import kotlinx.coroutines.launch

internal class TextInputModel(
    viewInfo: TextInputInfo,
    private val formState: SharedState<State.Form>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<TextInputView, TextInputInfo, TextInputModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun restoreValue(value: String)
    }

    init {
        formState.update { state ->
            state.copyWithFormInput(
                FormData.TextInput(
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
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
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

    override fun onViewAttached(view: TextInputView) {
        // Listen to text changes
        viewScope.launch {
            view.textChanges().collect { value ->
                    formState.update { state ->
                        state.copyWithFormInput(
                            FormData.TextInput(
                                identifier = viewInfo.identifier,
                                value = value,
                                isValid = !viewInfo.isRequired || value.isNotEmpty(),
                                attributeName = viewInfo.attributeName,
                                attributeValue = if (value.isNotEmpty()) {
                                    AttributeValue.wrap(value)
                                } else {
                                    null
                                }
                            )
                        )
                    }

                    if (viewInfo.eventHandlers.hasFormInputHandler()) {
                        handleViewEvent(EventHandler.Type.FORM_INPUT, value)
                    }
                }
        }

        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }
}
