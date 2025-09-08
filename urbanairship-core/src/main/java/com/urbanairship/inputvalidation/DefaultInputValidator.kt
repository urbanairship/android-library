/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import com.urbanairship.UALog
import com.urbanairship.channel.SmsValidationHandler
import com.urbanairship.config.AirshipRuntimeConfig
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield

/**
 * A default implementation of the [AirshipInputValidation.Validator] protocol that uses a standard SMS validation API.
 *
 * @hide
 */
internal class DefaultInputValidator(
    private val apiClient: SmsValidatorApiInterface,
    private val overrides: AirshipValidationOverride? = null
): AirshipInputValidation.Validator {

    constructor(config: AirshipRuntimeConfig): this(
        apiClient = CachingSmsValidatorApiClient(
            client = SmsValidatorApiClient(config)
        ),
        overrides = config.configOptions.validationOverride
    )

    private val _smsDelegate = MutableStateFlow<SmsValidationHandler?>(null)

    override val legacySmsDelegate: StateFlow<SmsValidationHandler?>
        get() = _smsDelegate.asStateFlow()

    override fun setLegacySmsDelegate(delegate: SmsValidationHandler?) {
        _smsDelegate.update { delegate }
    }

    /**
     *
     * Validates the provided request asynchronously.
     *
     * @param request The request to be validated (either SMS or Email).
     *
     * @return The validation result, either valid or invalid.
     */
    @Throws(IllegalArgumentException::class)
    override suspend fun validate(request: AirshipInputValidation.Request): AirshipInputValidation.Result {
        yield()

        UALog.d { "Validating input request $request" }
        overrides?.let {
            UALog.d { "Attempting to use overrides for request $request" }
            when(val value = it.getSuspending(request)) {
                is AirshipInputValidation.Override.Replace -> {
                    UALog.d { "Overrides result ${value.result} for request $request" }
                    return value.result
                }
                else -> {}
            }
        }

        yield()

        val result = when(request) {
            is AirshipInputValidation.Request.ValidateEmail -> validate(request.email, request)
            is AirshipInputValidation.Request.ValidateSms -> validate(request.sms, request)
        }

        UALog.d { "Result $result for request $request" }
        return result
    }

    /**
     * Validates an email address.
     *
     * @param email The email to be validated.
     * @param request The original request associated with the email.
     *
     * @return The result of the email validation, either valid or invalid.
     */
    private fun validate(
        email: AirshipInputValidation.Request.Email,
        request: AirshipInputValidation.Request
    ): AirshipInputValidation.Result {
        val address = email.rawInput.trimSpaceAndNewLine()

        return if (EMAIL_REGEX.containsMatchIn(address)) {
            AirshipInputValidation.Result.Valid(address)
        } else {
            AirshipInputValidation.Result.Invalid
        }
    }

    /**
     * Validates an SMS number.
     *
     * @param sms The SMS object containing validation information.
     * @param request The original request associated with the SMS.
     *
     * @return The result of the SMS validation, either valid or invalid.
     */
    @Throws(IllegalArgumentException::class)
    private suspend fun validate(
        sms: AirshipInputValidation.Request.Sms,
        request: AirshipInputValidation.Request
    ): AirshipInputValidation.Result {
        if (sms.validationHints?.matches(sms.rawInput) == false) {
            return AirshipInputValidation.Result.Invalid
        }

        val response = when(val option = sms.validationOptions) {
            is AirshipInputValidation.Request.Sms.ValidationOptions.Sender -> {
                _smsDelegate.value?.let {
                    // Legacy delegate validation
                    UALog.v { "Using deprecated SmsValidatorDelegate for $request" }
                    return legacySmsValidation(
                        address = sms.rawInput,
                        senderId = option.senderId,
                        prefix = option.prefix,
                        delegate = it
                    )
                }

                apiClient.validateSmsWithSender(
                    msisdn = sms.rawInput,
                    sender = option.senderId
                )
            }
            is AirshipInputValidation.Request.Sms.ValidationOptions.Prefix -> {
                apiClient.validateSmsWithPrefix(
                    msisdn = sms.rawInput,
                    prefix = option.prefix
                )
            }
        }

        // Assume client errors are not valid
        if (response.isClientError) {
            return AirshipInputValidation.Result.Invalid
        }

        // Make sure we have a result, if not throw an error
        val result = response.value
        if (!response.isSuccessful || result == null) {
            throw IllegalArgumentException("Failed to validate SMS $request")
        }

        return when(result) {
            SmsValidatorApiClient.Result.Invalid -> AirshipInputValidation.Result.Invalid
            is SmsValidatorApiClient.Result.Valid -> AirshipInputValidation.Result.Valid(result.address)
        }
    }

    /**
     * Performs legacy SMS validation using a deprecated delegate.
     *
     * @param address The phone number to be validated.
     * @param senderId The sender ID for validation.
     * @param prefix An optional prefix for the validation.
     * @param delegate The legacy delegate that will handle the SMS validation.
     *
     * @return The result of the validation, either valid or invalid.
     */
    private suspend fun legacySmsValidation(
        address: String,
        senderId: String,
        prefix: String?,
        delegate: SmsValidationHandler
    ): AirshipInputValidation.Result {
        val trimmed = address.trimSpaceAndNewLine()
        var cleanedNumber = trimmed.replace("[^0-9]".toRegex(), "")

        if (prefix != null) {
            val countryCode = prefix.replace("[^0-9]".toRegex(), "")
            if (!cleanedNumber.startsWith(countryCode)) {
                cleanedNumber = countryCode + cleanedNumber
            }
        }

        return if (delegate.validateSms(cleanedNumber, senderId)) {
            AirshipInputValidation.Result.Valid(cleanedNumber)
        } else {
            AirshipInputValidation.Result.Invalid
        }
    }

    internal companion object {
        /** Regular expression for validating email addresses. */
        private val EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s.]+$".toRegex()
    }
}

private suspend fun AirshipValidationOverride.getSuspending(
    request: AirshipInputValidation.Request
): AirshipInputValidation.Override? =
    suspendCoroutine { cont ->
        val result = getOverrides(request).get()
        cont.resume(result)
    }

/** Extension to add matching logic for SMS validation hints (e.g., minimum or maximum digits). */
private fun AirshipInputValidation.Request.Sms.ValidationHints.matches(input: String): Boolean {
    val digits = input.filter { it.isDigit() }

    return digits.length in minDigits..maxDigits
}

private fun String.trimSpaceAndNewLine(): String {
    return this
        .replace("\n", "")
        .trim()

}
