package com.urbanairship.channel

import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil

/**
 * Interface for objects that can validate SMS messages.
 */
internal interface SmsValidator {
    /**
     * Listener that can be set to override the default SMS validation behavior.
     */
    var listener: SmsValidationListener?

    /**
     * Validates a given MSISDN and sender.
     *
     * @param msisdn The MSISDN to validate.
     * @param sender The identifier given to the sender of the SMS message.
     * @return `true` if the MSISDN and sender are valid, otherwise `false`.
     */
    suspend fun validateSms(msisdn: String, sender: String): Boolean
}

/**
 * Listener interface for validating SMS messages.
 */
public interface SmsValidationListener {

    /**
     * Validates a given MSISDN and sender.
     *
     * @param msisdn The MSISDN to validate.
     * @param sender The identifier given to the sender of the SMS message.
     * @return `true` if the MSISDN and sender are valid, otherwise `false`.
     */
    public suspend fun validateSms(msisdn: String, sender: String): Boolean
}

/**
 * Data class representing the body of an SMS validation request.
 *
 * @param sender The identifier of the sender.
 * @param msisdn The MSISDN to be validated.
 */
internal data class SmsValidationBody(
    val sender: String,
    val msisdn: String
) : JsonSerializable {
    companion object {
        private const val REQUEST_MSISDN_KEY = "msisdn"
        private const val REQUEST_SENDER_KEY = "sender"
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            REQUEST_SENDER_KEY to sender,
            REQUEST_MSISDN_KEY to msisdn
        ).toJsonValue()
    }
}

/**
 * Interface for the SMS validation API client.
 */
internal interface SmsValidatorAPIClient {
    /**
     * Validates a given MSISDN and sender using the API.
     *
     * @param msisdn The MSISDN to validate.
     * @param sender The identifier given to the sender of the SMS message.
     * @return `true` if the MSISDN and sender are valid, otherwise `false`.
     */
    suspend fun validateSms(msisdn: String, sender: String): Boolean
}

@OpenForTesting
internal class SmsValidatorImpl(
    private val apiClient: SmsValidatorAPIClient,
    @Volatile override var listener: SmsValidationListener? = null
) : SmsValidator {

    private val resultsCache = mutableListOf<String>()
    private val resultsLookup = mutableMapOf<String, Boolean>()

    private suspend fun cacheResult(key: String, result: Boolean) {
        if (resultsCache.size >= 10) {
            val oldestKey = resultsCache.removeAt(0)
            resultsLookup.remove(oldestKey)
        }
        resultsCache.add(key)
        resultsLookup[key] = result
    }

    private suspend fun getCachedResult(key: String): Boolean? {
        return resultsLookup[key]
    }

    override suspend fun validateSms(msisdn: String, sender: String): Boolean {
        val compoundKey = "$sender$msisdn"

        getCachedResult(compoundKey)?.let {
            return it
        }

        listener?.let {
            val isValid = it.validateSms(msisdn, sender)
            cacheResult(compoundKey, isValid)
            return isValid
        }

        val isValid = apiClient.validateSms(msisdn, sender)
        cacheResult(compoundKey, isValid)
        return isValid
    }
}

internal class SmsValidatorAPIClientImpl(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession
) : SmsValidatorAPIClient {

    private companion object {
        private const val RESPONSE_OK_KEY = "ok"
        private const val RESPONSE_VALID_KEY = "valid"
        private const val VALIDATION_PATH = "api/channels/sms/validate"
    }

    override suspend fun validateSms(msisdn: String, sender: String): Boolean {
        val responseBody = performSmsValidation(SmsValidationBody(sender, msisdn)).value
        return responseBody ?: false
    }

    private suspend fun performSmsValidation(payload: JsonSerializable): RequestResult<Boolean> {
        val url = runtimeConfig.deviceUrl.appendEncodedPath(VALIDATION_PATH).build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "Content-Type" to "application/json",
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey
        )

        val request = Request(
            url = url,
            headers = headers,
            method = "POST",
            auth = RequestAuth.GeneratedAppToken,
            body = RequestBody.Json(payload)
        )

        UALog.d { "Attempting SMS validation: $request" }

        val result = session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            return@execute if (UAHttpStatusUtil.inSuccessRange(status)) {
                val json = JsonValue.parseString(responseBody).requireMap()
                val isOk = json.requireField<Boolean>(RESPONSE_OK_KEY)
                val valid = json.requireField<Boolean>(RESPONSE_VALID_KEY)

                valid && isOk
            } else {
                null
            }
        }.also { result ->
            UALog.d { "SMS Channel validation finished with result: $result" }
        }

        return result
    }
}
