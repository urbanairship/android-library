package com.urbanairship.channel

/**
 * Handler interface that can be used to override the default SMS validation behavior.
 */
@Deprecated("App should use `AirshipConfigOptions.validationOverrides` instead.")
public interface SmsValidationHandler {

    /**
     * Validates a given MSISDN and sender.
     *
     * @param msisdn The MSISDN to validate.
     * @param sender The identifier given to the sender of the SMS message.
     * @return `true` if the MSISDN and sender are valid, otherwise `false`.
     */
    public suspend fun validateSms(msisdn: String, sender: String): Boolean
}
