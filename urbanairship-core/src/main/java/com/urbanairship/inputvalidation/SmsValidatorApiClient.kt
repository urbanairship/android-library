/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import android.net.Uri
import com.urbanairship.UAirship
import com.urbanairship.inputvalidation.SmsValidatorApiClient.Result
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil
import java.security.InvalidParameterException

internal interface SmsValidatorApiInterface {
    @Throws(InvalidParameterException::class)
    suspend fun validateSmsWithSender(msisdn: String, sender: String): RequestResult<Result>

    @Throws(InvalidParameterException::class)
    suspend fun validateSmsWithPrefix(msisdn: String, prefix: String): RequestResult<Result>
}

internal class SmsValidatorApiClient(
    private val config: AirshipRuntimeConfig,
    private var session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
): SmsValidatorApiInterface {

    @Throws(InvalidParameterException::class)
    override suspend fun validateSmsWithSender(msisdn: String, sender: String): RequestResult<Result> {
        return performRequest(
            Body(msisdn = msisdn, sender = sender)
        )
    }

    @Throws(InvalidParameterException::class)
    override suspend fun validateSmsWithPrefix(msisdn: String, prefix: String): RequestResult<Result> {
        return performRequest(
            Body(msisdn = msisdn, prefix = prefix)
        )
    }

    @Throws(InvalidParameterException::class)
    private suspend fun performRequest(body: JsonSerializable): RequestResult<Result> {

        val platform = when (config.platform) {
            UAirship.ANDROID_PLATFORM -> "android"
            UAirship.AMAZON_PLATFORM -> "amazon"
            else -> null
        } ?: throw InvalidParameterException("Invalid platform")

        val headers = mutableMapOf(
            "X-UA-Lib-Version" to UAirship.getVersion(),
            "X-UA-Device-Family" to platform,
            "Content-Type" to "application/json",
            "Accept" to "application/vnd.urbanairship+json; version=3;",
        )

        val request = Request(
            url = requestUri(),
            method = "POST",
            headers = headers.toMap(),
            body = RequestBody.Json(body),
            auth = RequestAuth.GeneratedAppToken
        )

        return session.execute(request) { status, _, responseBody ->
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                JsonValue.parseString(responseBody).let(Result::fromJson)
            } else {
                null
            }
        }
    }

    @Throws(InvalidParameterException::class)
    private fun requestUri(): Uri {
        return config.deviceUrl.appendEncodedPath("api/channels/sms/format").build()
            ?: throw InvalidParameterException("Initial config not resolved.")
    }

    private data class Body(
        val msisdn: String,
        val sender: String? = null,
        val prefix: String? = null
    ): JsonSerializable {
        companion object {
            private const val MSISDN = "msisdn"
            private const val SENDER = "sender"
            private const val PREFIX = "prefix"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            MSISDN to msisdn,
            SENDER to sender,
            PREFIX to prefix
        ).toJsonValue()
    }

    internal sealed class Result() {
        data class Valid(val address: String): Result()
        data object Invalid: Result()

        companion object {
            private const val VALID = "valid"
            private const val MSISDN = "msisdn"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Result {
                val content = value.optMap()

                return if (content.requireField<Boolean>(VALID)) {
                    Valid(content.requireField(MSISDN))
                } else {
                    Invalid
                }
            }
        }
    }
}
