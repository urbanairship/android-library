/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.log
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil

/**
 * A high level abstraction for performing Channel requests.
 */
internal class ChannelApiClient @VisibleForTesting constructor(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = runtimeConfig.requestSession.toSuspendingRequestSession()
) {
    suspend fun createChannel(channelPayload: ChannelRegistrationPayload): RequestResult<Channel> {
        UALog.d { "Creating channel with payload: $channelPayload" }

        val builder = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(CHANNEL_API_PATH)

        val request = Request(
            url = builder.build(),
            method = "POST",
            auth = RequestAuth.GeneratedAppToken,
            body = RequestBody.Json(channelPayload),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;"
            )
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            return@execute if (UAHttpStatusUtil.inSuccessRange(status)) {
                val identifier = JsonValue.parseString(responseBody).requireMap().requireField<String>(CHANNEL_ID_KEY)
                Channel(
                    identifier = identifier,
                    location = builder.appendPath(identifier).build().toString()
                )
            } else {
                null
            }
        }.also { result ->
            result.log { "Creating channel finished with result: $result" }
        }
    }

    suspend fun updateChannel(channelId: String, channelPayload: ChannelRegistrationPayload): RequestResult<Channel> {
        UALog.d { "Updating channel $channelId with payload: $channelPayload" }

        val url = createLocation(channelId)

        val request = Request(
            url = url,
            method = "PUT",
            auth = RequestAuth.ChannelTokenAuth(channelId),
            body = RequestBody.Json(channelPayload),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;"
            )
        )

        return session.execute(request).map {
            Channel(channelId, url.toString())
        }.also { result ->
            result.log { "Updating channel $channelId finished with result: $result" }
        }
    }

    internal fun createLocation(channelId: String): Uri? {
        return runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(CHANNEL_API_PATH)
            .appendPath(channelId)
            .build()
    }

    companion object {
        private const val CHANNEL_API_PATH = "api/channels/"

        /**
         * Response body key for the channel ID.
         */
        private const val CHANNEL_ID_KEY = "channel_id"
    }
}

internal data class Channel(
    val identifier: String,
    val location: String
)
