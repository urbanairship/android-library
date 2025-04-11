package com.urbanairship.channel

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil
import kotlin.jvm.Throws

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
