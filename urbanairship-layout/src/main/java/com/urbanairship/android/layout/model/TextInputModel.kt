/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.util.onEditing
import com.urbanairship.android.layout.util.textChanges
import com.urbanairship.android.layout.view.TextInputView
import com.urbanairship.util.airshipIsValidEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class TextInputModel(
    viewInfo: TextInputInfo,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<TextInputView, TextInputInfo, TextInputModel.Listener>(
    viewInfo = viewInfo, environment = environment, properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun restoreValue(value: String)
    }

    init {
        formState.updateFormInput(
            value = ThomasFormField.TextInput(
                textInput = viewInfo.inputType,
                identifier = viewInfo.identifier,
                originalValue = null,
                filedType = ThomasFormField.FiledType.just(
                    value = "",
                    validator = { !viewInfo.isRequired },
                    attributes = ThomasFormField.makeAttributes(
                        name = viewInfo.attributeName,
                        value = null
                    )
                )
            ),
            pageId = properties.pagerPageId
        )

        modelScope.launch {
            formState.formUpdates.collect { state ->
                listener?.setEnabled(state.isEnabled)
            }
        }
    }

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = TextInputView(context, this).apply {
        id = viewId

        // Restore value, if available
        formState.inputData<ThomasFormField.TextInput>(viewInfo.identifier)?.let { input ->
            input.originalValue?.let { listener?.restoreValue(it) }
        }
    }

    override fun onViewCreated(view: TextInputView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(viewInfo.identifier, isDisplayed)
        }
    }

    private fun makeResolveMethod(text: String?): ThomasFormField.FiledType<String> {

        val attributes = ThomasFormField.makeAttributes(
            name = viewInfo.attributeName,
            value = if (text?.isNotEmpty() == true) AttributeValue.wrap(text) else null
        )

        val channelRegistration = channelRegistration(text)?.let { listOf(it) }

        if (text.isNullOrEmpty()) {
            return ThomasFormField.FiledType.just(
                value = text ?: "",
                validator = { !viewInfo.isRequired },
                attributes = attributes,
                channels = channelRegistration
            )
        }

        return when (viewInfo.inputType) {
            FormInputType.EMAIL -> {
                return ThomasFormField.FiledType.just(
                    value = text,
                    validator = { it?.airshipIsValidEmail() == true },
                    attributes = attributes,
                    channels = channelRegistration
                )
            }
            FormInputType.NUMBER -> {
                return ThomasFormField.FiledType.Async(
                    fetcher = ThomasFormField.AsyncValueFetcher(
                        //TODO: il replace with an actual implementation
                        fetchBlock = { ThomasFormField.AsyncValueFetcher.PendingResult.Valid(
                            ThomasFormField.Result(
                                value = "--validated--"
                        )) }
                    )
                )
            }
            FormInputType.TEXT -> ThomasFormField.FiledType.just(
                value = text,
                attributes = attributes,
                channels = channelRegistration
            )
            FormInputType.TEXT_MULTILINE -> ThomasFormField.FiledType.just(
                value = text,
                attributes = attributes,
                channels = channelRegistration
            )
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

                formState.updateFormInput(
                    value = makeFormField(value, validationState),
                    pageId = properties.pagerPageId
                )
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

    private fun makeFormField(
        input: String,
        validationStatus: MutableStateFlow<ValidationState?>
    ): ThomasFormField.TextInput {

        val updateValidationState = input.isNotEmpty() || validationStatus.value != null
        if (updateValidationState) {
            validationStatus.update { ValidationState.VALIDATING }
        }

        val trimmed = input.trim()
        val method = makeResolveMethod(trimmed)
        when(method) {
            is ThomasFormField.FiledType.Async -> {
                modelScope.launch {
                    when(method.fetcher.results.first { it != null }) {
                        is ThomasFormField.AsyncValueFetcher.PendingResult.Error -> {
                            //TODO: il retry?
                        }
                        is ThomasFormField.AsyncValueFetcher.PendingResult.Invalid -> {
                            validationStatus.update { ValidationState.INVALID }
                        }
                        is ThomasFormField.AsyncValueFetcher.PendingResult.Valid -> {
                            validationStatus.update { ValidationState.VALID }
                        }
                        null -> { }
                    }
                }
            }
            is ThomasFormField.FiledType.Instant -> {
                validationStatus.update {
                    if (method.result != null) ValidationState.VALID else ValidationState.INVALID
                }
            }
        }

        return ThomasFormField.TextInput(
            textInput = viewInfo.inputType,
            identifier = viewInfo.identifier,
            originalValue = input,
            filedType = method
        )
    }
}
