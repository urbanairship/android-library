/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.Airship
import com.urbanairship.android.layout.ai.ThomasAIInferenceOutcome
import com.urbanairship.android.layout.ai.ThomasAIInferenceRequest
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.SmsLocale
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.view.TextInputView
import com.urbanairship.inputvalidation.AirshipInputValidation
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    // We need to use the resolved id for the editTextViewId to make labelFor work since
    // a TextInputView is a linear layout that wraps an edit text.
    override val viewId: Int = environment.viewIdResolver.viewId()
    val editTextViewId = environment.viewIdResolver.viewId(viewInfo.identifier, viewInfo.type)

    internal fun onNewLocale(smsLocale: SmsLocale) {
        _smsLocale.update { smsLocale }
    }
    private val _smsLocale = MutableStateFlow<SmsLocale?>(null)

    val selectedLocale: SmsLocale?
        get() = _smsLocale.value

    interface Listener : BaseModel.Listener {

        fun restoreValue(value: String)
    }

    private val currentInput = MutableStateFlow("")

    /**
     * The last completed inference and the text it was run on, so text that comes back
     * unchanged — a restore, or an edit to trailing whitespace — reuses the answer instead of
     * paying for the model again.
     */
    private var lastInference: Pair<String, ThomasAIInferenceOutcome>? = null

    private val inputValidator: AirshipInputValidation.Validator?
        get() {
            if (!Airship.isFlyingOrTakingOff) {
                return null
            }

            return Airship.inputValidator
        }

    init {
        val initialValue = formState.getInitialValue(viewInfo.identifier)?.let(ThomasFormField.TextInput::decodeState)
        _smsLocale.value = initialValue?.smsLocale

        formState.updateFormInput(
            value = ThomasFormField.TextInput(
                textInput = viewInfo.inputType,
                smsLocale = _smsLocale.value,
                identifier = viewInfo.identifier,
                originalValue = initialValue?.value,
                fieldType = ThomasFormField.FieldType.just(
                    value = "",
                    validator = { !viewInfo.isRequired },
                    attributes = ThomasFormField.makeAttributes(
                        name = viewInfo.attributeName,
                        value = null
                    )
                ),
                isRedacted = viewInfo.redactInput
            ),
            pageId = properties.pagerPageId
        )

        modelScope.launch {
            formState.formUpdates.collect { state ->
                listener?.setEnabled(state.isEnabled)
            }
        }

        wireValidationActions(
            identifier = viewInfo.identifier,
            thomasForm = formState,
            initialValue = currentInput.value,
            valueUpdates = currentInput,
            validatable = viewInfo
        )
    }

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = TextInputView(context, this).apply {
        id = viewId

        // Restore value, if available
        formState.inputData<ThomasFormField.TextInput>(viewInfo.identifier)?.let { input ->
            input.originalValue?.let { text ->
                currentInput.update { text }
                listener?.restoreValue(text)
            }
        }
    }

    override fun onViewCreated(view: TextInputView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(viewInfo.identifier, isDisplayed)
        }
    }

    private fun makeResolveMethod(text: String?, smsLocale: SmsLocale?): ThomasFormField.FieldType<String> {

        val attributes = ThomasFormField.makeAttributes(
            name = viewInfo.attributeName,
            value = if (text?.isNotEmpty() == true) AttributeValue.wrap(text) else null
        )

        if (text.isNullOrEmpty()) {
            return ThomasFormField.FieldType.just(
                value = text ?: "",
                validator = { !viewInfo.isRequired },
                attributes = attributes,
                channels = channelRegistration(text)?.let { listOf(it) }
            )
        }

        return when (viewInfo.inputType) {
            // Inference fields (nothing to validate)
            FormInputType.NUMBER -> inferenceField(text, attributes)
            FormInputType.TEXT -> inferenceField(text, attributes)
            FormInputType.TEXT_MULTILINE -> inferenceField(text, attributes)

            // EMAIL/SMS intentionally excluded from inference. We only validate them.
            FormInputType.EMAIL -> {
                val request = AirshipInputValidation.Request.ValidateEmail(
                    AirshipInputValidation.Request.Email(text)
                )

                ThomasFormField.FieldType.Async(
                    fetcher = ThomasFormField.AsyncValueFetcher(
                        processDelay = (1.5).seconds,
                        fetchBlock = {
                            val validator = inputValidator
                                ?: return@AsyncValueFetcher ThomasFormField.AsyncValueFetcher.PendingResult.Invalid()

                            when(val result = validator.validate(request)) {
                                AirshipInputValidation.Result.Invalid -> {
                                    ThomasFormField.AsyncValueFetcher.PendingResult.Invalid()
                                }
                                is AirshipInputValidation.Result.Valid -> {
                                    ThomasFormField.AsyncValueFetcher.PendingResult.Valid(
                                        result = ThomasFormField.Result(
                                            value = result.address,
                                            channels = channelRegistration(result.address)?.let { listOf(it) },
                                            attributes = attributes
                                        )
                                    )
                                }
                            }
                        }
                    )
                )
            }
            FormInputType.SMS -> {
                val selectedLocale = smsLocale ?: return ThomasFormField.FieldType.just(
                    value = text,
                    validator = { false }
                )

                val request = AirshipInputValidation.Request.ValidateSms(
                    sms = AirshipInputValidation.Request.Sms(
                        rawInput = text,
                        validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Prefix(
                            selectedLocale.prefix
                        ),
                        validationHints = AirshipInputValidation.Request.Sms.ValidationHints(
                            minDigits = selectedLocale.validationHints?.minDigits,
                            maxDigits = selectedLocale.validationHints?.maxDigits
                        )
                    )
                )

                ThomasFormField.FieldType.Async(
                    fetcher = ThomasFormField.AsyncValueFetcher(
                        fetchBlock = {
                            val validator = inputValidator
                                ?: return@AsyncValueFetcher ThomasFormField.AsyncValueFetcher.PendingResult.Invalid()

                            when(val result = validator.validate(request)) {
                                AirshipInputValidation.Result.Invalid -> {
                                    ThomasFormField.AsyncValueFetcher.PendingResult.Invalid()
                                }
                                is AirshipInputValidation.Result.Valid -> {
                                    ThomasFormField.AsyncValueFetcher.PendingResult.Valid(
                                        result = ThomasFormField.Result(
                                            value = result.address,
                                            channels = channelRegistration(result.address)?.let { listOf(it) },
                                            attributes = attributes
                                        )
                                    )
                                }
                            }
                        }
                    )
                )
            }
        }
    }

    /**
     * Returns the field for an input with no address to validate: valid as typed, carrying the
     * payload's AI inference — if it asked for any — post-processed onto the result.
     *
     * Inference never decides validity. A missing model, a skipped evaluation and a failed one
     * all resolve valid carrying [ThomasAIInferenceOutcome.Failed], so a layout can branch to a
     * non-AI path instead of the form stalling on an answer that isn't coming.
     *
     * @param text The trimmed input.
     * @param attributes The attributes the input sets.
     * @return The field.
     */
    private fun inferenceField(
        text: String,
        attributes: Map<AttributeName, AttributeValue>?
    ): ThomasFormField.FieldType<String> {
        val result = ThomasFormField.Result(
            value = text,
            channels = channelRegistration(text)?.let { listOf(it) },
            attributes = attributes
        )

        val inference = viewInfo.aiInference
            ?: return ThomasFormField.FieldType.Instant(result)

        lastInference?.let { (inferred, outcome) ->
            if (inferred == text) {
                return ThomasFormField.FieldType.Instant(result.copy(aiInference = outcome))
            }
        }

        val executor = environment.aiInference
        if (executor == null || !executor.isAvailable) {
            // Resolved without the settle delay: there is nothing to wait for.
            return ThomasFormField.FieldType.Instant(
                result.copy(aiInference = ThomasAIInferenceOutcome.Failed)
            )
        }

        val request = ThomasAIInferenceRequest(
            prompt = inference.prompt,
            text = text,
            outputSchema = inference.outputSchema,
            additionalContext = inference.additionalContext,
            subjectHints = inference.subjectHints
        )

        // Post-processed through the model the way email and SMS post-process through
        // validation, so the async field machinery supplies the settle delay, cancellation on
        // newer input, and the pending status that keeps the field out of a submit.
        return ThomasFormField.FieldType.Async(
            fetcher = ThomasFormField.AsyncValueFetcher(
                processDelay = AI_INFERENCE_PROCESS_DELAY,
                fetchBlock = {
                    val outcome = executor.run(request)
                        ?.let { ThomasAIInferenceOutcome.Complete(it, inference.outputSchema) }
                        ?: ThomasAIInferenceOutcome.Failed

                    // Only a real answer is memoized. A cached failure is never retried:
                    // the early return above short-circuits before the fetcher, and the
                    // fetcher's own retry backoff never applies because a failure resolves
                    // as `Valid`, not `Error`.
                    if (outcome is ThomasAIInferenceOutcome.Complete) {
                        lastInference = text to outcome
                    }

                    ThomasFormField.AsyncValueFetcher.PendingResult.Valid(
                        result = result.copy(aiInference = outcome)
                    )
                }
            )
        )
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
            FormInputType.SMS -> {
                _smsLocale.value?.registration?.let { ThomasChannelRegistration.Sms(address, it) }
            }
            FormInputType.TEXT -> null
            FormInputType.TEXT_MULTILINE -> null
        }
    }

    enum class ValidationState {
        VALIDATING, VALID, INVALID
    }

    override fun onViewAttached(view: TextInputView) {
        // Listen to text changes
        val validationState = MutableStateFlow<ValidationState?>(null)

        viewScope.launch {
            combine(view.textChanges(), _smsLocale) { text, locale -> Pair(text, locale) }
                .collect { (text, locale) ->
                    currentInput.update { text }
                    formState.updateFormInput(
                        value = makeFormField(text, locale, validationState),
                        pageId = properties.pagerPageId
                    )
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
        smsLocale: SmsLocale?,
        validationStatus: MutableStateFlow<ValidationState?>
    ): ThomasFormField.TextInput {

        val updateValidationState = input.isNotEmpty() || validationStatus.value != null
        if (updateValidationState) {
            validationStatus.update { ValidationState.VALIDATING }
        }

        val trimmed = input.trim()
        val method = makeResolveMethod(trimmed, smsLocale)
        when(method) {
            is ThomasFormField.FieldType.Async -> {
                modelScope.launch {
                    when(method.fetcher.results.first { it != null }) {
                        is ThomasFormField.AsyncValueFetcher.PendingResult.Error -> { }
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
            is ThomasFormField.FieldType.Instant -> {
                validationStatus.update {
                    if (method.result != null) ValidationState.VALID else ValidationState.INVALID
                }
            }
        }

        return ThomasFormField.TextInput(
            textInput = viewInfo.inputType,
            smsLocale = smsLocale,
            identifier = viewInfo.identifier,
            originalValue = input,
            fieldType = method,
            isRedacted = viewInfo.redactInput
        )
    }

    private companion object {
        /**
         * How long the input must be idle before it goes to the model. Longer than
         * validation's delay — an evaluation costs far more than an address lookup.
         */
        val AI_INFERENCE_PROCESS_DELAY = 2.seconds
    }
}
