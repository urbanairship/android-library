/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import androidx.annotation.RestrictTo
import com.urbanairship.PendingResult
import kotlinx.coroutines.flow.StateFlow

/** A closure type used for overriding validation logic. */
public interface AirshipValidationOverride {
    public fun getOverrides(request: AirshipInputValidation.Request): PendingResult<AirshipInputValidation.Override>
}

/** A class that encapsulates input validation logic for different request types such as email and SMS. */
public class AirshipInputValidation private constructor(){

    /**
     * Class representing the result of validation.
     * It indicates whether an input is valid or invalid.
     */
    public sealed class Result {
        /** Indicates a valid input with the associated address (e.g., email or phone number). */
        public class Valid(public val address: String): Result()

        /** Indicates an invalid input. */
        public data object Invalid: Result()

        override fun toString(): String {
            val data = when(this) {
                Invalid -> "Invalid()"
                is Valid -> "Valid(address = $address)"
            }
            return "Result($data)"
        }
    }

    /** Class representing the override options for input validation. */
    public sealed class Override {
        /** Override the result of validation with a custom validation result. */
        public class Replace(public val result: Result): Override()

        /** Skip the override and use the default validation method. */
        public data object UseDefault: Override()
    }

    /**
     * Class representing the types of requests to be validated (e.g., Email or SMS).
     *
     * @hide
     * */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public sealed class Request {
        public class ValidateEmail(public val email: Email): Request()
        public class ValidateSms(public val sms: Sms): Request()

        /** A class representing an email request for validation. */
        public class Email(
            /** The raw email input to be validated. */
            public val rawInput: String
        )

        /** A class representing an SMS request for validation. */
        public class Sms(
            /** The raw input string to be validated. */
            public val rawInput: String,

            /** The validation options to be applied. */
            public val validationOptions: ValidationOptions,

            /** Optional validation hints such as min/max digit constraints. */
            public val validationHints: ValidationHints? = null
        ) {

            /** Class specifying the options for validating an SMS, such as sender ID or prefix. */
            public sealed class ValidationOptions {
                public class Sender(
                    public val senderId: String,
                    public val prefix: String? = null
                ): ValidationOptions()

                public class Prefix(public val prefix: String): ValidationOptions()

                override fun toString(): String {
                    val data = when(this) {
                        is Prefix -> "prefix = $prefix"
                        is Sender -> "sender = ${senderId}, prefix = $prefix"
                    }
                    return "ValidationOptions($data)"
                }
            }

            /** A class for defining validation hints like min/max digit requirements. */
            public class ValidationHints(
                public val minDigits: Int = 0,
                public val maxDigits: Int = Int.MAX_VALUE
            ) {
                public constructor(minDigits: Int? = null, maxDigits: Int? = null): this(
                    minDigits = minDigits ?: 0,
                    maxDigits = maxDigits ?: Int.MAX_VALUE
                )
            }
        }

        override fun toString(): String {
            val data = when(this) {
                is ValidateEmail -> "email = ${email.rawInput}"
                is ValidateSms -> "sms = ${sms.rawInput}, validationOptions = ${sms.validationOptions}"
            }
            return "Request($data)"
        }
    }

    /**
     * Interface for validators that perform validation of input requests.
     *
     * @hide
     * */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Validator {
        public val legacySmsDelegate: StateFlow<SmsValidatorDelegate?>

        /**
         * Validates the provided request and returns a result.
         *
         * @param request The request to be validated (either [Request.ValidateSms] or [Request.ValidateEmail]).
         * @return The validation result, either valid or invalid.
         */
        public suspend fun validate(request: Request): Result
    }
}

/**
 * Delegate for overriding the default SMS validation.
 * Deprecated, app should use `AirshipConfig.inputValidationOverrides` instead.
 */
public interface SmsValidatorDelegate {

    /**
     * Validates a given MSISDN.
     *
     * @param msisdn: The msisdn to validate.
     * @param sender: The identifier given to the sender of the SMS message.
     * @return `true` if the phone number is valid, otherwise `false`.
     */
    public suspend fun validateSms(msisdn: String, sender: String): Boolean
}
