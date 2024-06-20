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

/**
 * Interface for objects that can validate SMS messages.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface SmsValidator {
    /**
     * Handler that can be set to override the default SMS validation behavior.
     */
    public var handler: SmsValidationHandler?

    /**
     * Validates a given MSISDN and sender.
     *
     * @param msisdn The MSISDN to validate.
     * @param sender The identifier given to the sender of the SMS message.
     * @return `true` if the MSISDN and sender are valid, otherwise `false`.
     */
    public suspend fun validateSms(msisdn: String, sender: String): Boolean
}

@OpenForTesting
internal class AirshipSmsValidator(
    private val apiClient: AirshipSmsValidatorApiClient,
) : SmsValidator {

    constructor(config: AirshipRuntimeConfig) : this(
        AirshipSmsValidatorApiClient(config)
    )

    override var handler: SmsValidationHandler? = null

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

        handler?.let {
            val isValid = it.validateSms(msisdn, sender)
            cacheResult(compoundKey, isValid)
            return isValid
        }

        val isValid = apiClient.validateSms(msisdn, sender)
        cacheResult(compoundKey, isValid)
        return isValid
    }
}

internal class AirshipSmsValidatorApiClient(
    private val config: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
) {

    suspend fun validateSms(msisdn: String, sender: String): Boolean {
        val payload = try {
            requestPayload(msisdn, sender)
        } catch (e: JsonException) {
            UALog.e(e) { "Failed to create SMS validation request!" }
            return false
        }

        return performSmsValidation(payload).value ?: false
    }

    private suspend fun performSmsValidation(payload: JsonMap): RequestResult<Boolean> {
        val url = config.deviceUrl.appendEncodedPath(VALIDATION_PATH).build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "Content-Type" to "application/json",
            "X-UA-Appkey" to config.configOptions.appKey
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
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                try {
                    val json = JsonValue.parseString(responseBody).requireMap()

                    val isOk: Boolean = json.requireField(RESPONSE_OK_KEY)
                    val isValid: Boolean = json.requireField(RESPONSE_VALID_KEY)

                    isOk && isValid
                } catch (e: JsonException) {
                    UALog.e(e) { "Failed to parse SMS validation response!" }
                    null
                }
            } else {
                UALog.e { "Failed to validate SMS! (status: $status)" }
                null
            }
        }

        UALog.d { "SMS Channel validation finished with result: $result" }

        return result
    }

    internal companion object {
        private const val RESPONSE_OK_KEY = "ok"
        private const val RESPONSE_VALID_KEY = "valid"
        private const val VALIDATION_PATH = "api/channels/sms/validate"

        private const val REQUEST_MSISDN_KEY = "msisdn"
        private const val REQUEST_SENDER_KEY = "sender"

        @VisibleForTesting
        @Throws(JsonException::class)
        fun requestPayload(msisdn: String, sender: String): JsonMap = jsonMapOf(
            REQUEST_MSISDN_KEY to msisdn,
            REQUEST_SENDER_KEY to sender,
        )
    }
}
