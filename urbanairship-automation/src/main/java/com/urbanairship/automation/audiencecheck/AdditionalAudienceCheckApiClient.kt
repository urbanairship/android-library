package com.urbanairship.automation.audiencecheck

import android.net.Uri
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.ResponseParser
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil
import java.security.InvalidParameterException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class AdditionalAudienceCheckApiClient(
    private val config: AirshipRuntimeConfig,
    private var session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
) {

    @Throws(InvalidParameterException::class)
    suspend fun resolve(info: Info): RequestResult<Result> {
        val platform = when (config.platform) {
            Platform.ANDROID -> "android"
            Platform.AMAZON -> "amazon"
            else -> null
        } ?: return RequestResult(exception = InvalidParameterException("Invalid platform"))

        val headers = mutableMapOf(
            "X-UA-Contact-ID" to info.contactId,
            "X-UA-Device-Family" to platform,
            "Content-Type" to "application/json",
            "Accept" to "application/vnd.urbanairship+json; version=3;",
        )

        val request = Request(
            url = Uri.parse(info.url),
            method = "POST",
            headers = headers.toMap(),
            body = RequestBody.Json(info),
            auth = RequestAuth.ContactTokenAuth(info.contactId)
        )

        return session.execute(request, ResponseParser { status, _, responseBody ->
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return@ResponseParser null
            }

            responseBody
                ?.let { JsonValue.parseString(it) }
                ?.let(Result::fromJson)
        })
    }

    data class Result(
        val isMatched: Boolean,
        val cacheTtl: Duration
    ) : JsonSerializable {
        companion object {
            private const val IS_MATCHED = "allowed"
            private const val CACHE_TTL = "cache_seconds"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Result {
                val content = value.requireMap()
                return Result(
                    isMatched = content.requireField(IS_MATCHED),
                    cacheTtl = content.requireField<Long>(CACHE_TTL).seconds
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IS_MATCHED to isMatched,
            CACHE_TTL to cacheTtl.inWholeSeconds
        ).toJsonValue()
    }

    internal data class Info(
        val url: String,
        val channelId: String,
        val contactId: String,
        val namedUserId: String?,
        val context: JsonValue?,
    ) : JsonSerializable {

        companion object {
            private const val CHANNEL_ID = "channel_id"
            private const val CONTACT_ID = "contact_id"
            private const val NAMED_USER_ID = "named_user_id"
            private const val CONTEXT = "context"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            CHANNEL_ID to channelId,
            CONTACT_ID to contactId,
            NAMED_USER_ID to namedUserId,
            CONTEXT to context
        ).toJsonValue()
    }
}
