/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestAuth.BasicAuth
import com.urbanairship.http.RequestAuth.ChannelTokenAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.http.RequestSession
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil

/**  high level abstraction for performing Inbox API requests. */
internal class InboxApiClient(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: RequestSession = runtimeConfig.requestSession
) {

    suspend fun fetchMessages(
        user: UserCredentials,
        channelId: String,
        ifModifiedSince: String?
    ): RequestResult<JsonList> {
        val url = getUserApiUrl(user.username, MESSAGES_PATH)

        val headers = mutableMapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            CHANNEL_ID_HEADER to channelId
        )

        ifModifiedSince?.let { headers["If-Modified-Since"] = it }

        val request = Request(url, "GET", getUserAuth(user), null, headers.toMap())

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            return@execute if (UAHttpStatusUtil.inSuccessRange(status)) {
                JsonValue.parseString(responseBody).optMap().opt("messages").requireList()
            } else {
                null
            }
        }
    }

    suspend fun syncDeletedMessageState(
        user: UserCredentials,
        channelId: String,
        reportingsToDelete: List<JsonValue>
    ): RequestResult<Unit> {
        val url = getUserApiUrl(user.username, DELETE_MESSAGES_PATH)
        val payload = jsonMapOf(MESSAGES_REPORTINGS_KEY to JsonValue.wrapOpt(reportingsToDelete))

        UALog.v { "Deleting inbox messages with payload: $payload" }

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            CHANNEL_ID_HEADER to channelId
        )

        val request = Request(url, "POST", getUserAuth(user), RequestBody.Json(payload), headers)

        return session.execute(request)
    }

    suspend fun syncReadMessageState(
        user: UserCredentials,
        channelId: String,
        reportingsToUpdate: List<JsonValue>
    ): RequestResult<Unit> {
        val url = getUserApiUrl(user.username, MARK_READ_MESSAGES_PATH)
        val payload = jsonMapOf(MESSAGES_REPORTINGS_KEY to JsonValue.wrapOpt(reportingsToUpdate))

        UALog.v { "Marking inbox messages read request with payload: $payload" }

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            CHANNEL_ID_HEADER to channelId
        )

        val request = Request(url, "POST", getUserAuth(user), RequestBody.Json(payload), headers)

        return session.execute(request)
    }

    suspend fun createUser(channelId: String): RequestResult<UserCredentials> {
        val url = getUserApiUrl()
        val channelKey = payloadChannelsKey ?: return errorResult("Missing platform")
        val payload = JsonMap.newBuilder().putOpt(channelKey, listOf(channelId)).build()

        UALog.v("Creating Rich Push user with payload: %s", payload)

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            CHANNEL_ID_HEADER to channelId
        )

        val request = Request(url, "POST", ChannelTokenAuth(channelId), RequestBody.Json(payload), headers)

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            return@execute if (UAHttpStatusUtil.inSuccessRange(status)) {
                val credentials = JsonValue.parseString(responseBody).requireMap()
                val userId = credentials.requireField<String>("user_id")
                val userToken = credentials.requireField<String>("password")
                UserCredentials(userId, userToken)
            } else {
                null
            }
        }

    }

    suspend fun updateUser(user: UserCredentials, channelId: String): RequestResult<Unit> {
        val channelKey = payloadChannelsKey ?: return errorResult("Missing platform")
        val payload = jsonMapOf(channelKey to jsonMapOf(PAYLOAD_ADD_KEY to listOf(channelId)))

        val url = getUserApiUrl(user.username)

        UALog.v { "Updating user with payload: $payload" }

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            CHANNEL_ID_HEADER to channelId
        )

        val request = Request(url, "POST", getUserAuth(user), RequestBody.Json(payload), headers)

        return session.execute(request)
    }

    /**
     * Gets the URL for inbox/user api calls
     *
     * @param paths Additional paths.
     * @return The URL or null if an error occurred.
     */
    private fun getUserApiUrl(vararg paths: String): Uri? {
        val builder = runtimeConfig.deviceUrl.appendEncodedPath(USER_API_PATH)

        for (p in paths) {
            var path = p
            if (!path.endsWith("/")) {
                path = "$path/"
            }
            builder.appendEncodedPath(path)
        }

        return builder.build()
    }

    /** The payload channels key based on the platform. */
    private val payloadChannelsKey: String?
        get() = when (runtimeConfig.platform) {
            Platform.AMAZON -> PAYLOAD_AMAZON_CHANNELS_KEY
            Platform.ANDROID -> PAYLOAD_ANDROID_CHANNELS_KEY
            else -> null
        }

    private fun getUserAuth(user: UserCredentials): RequestAuth {
        return BasicAuth(user.username, user.password)
    }

    private fun  <T> errorResult(message: String): RequestResult<T> {
        return RequestResult(RequestException(message))
    }

    private companion object {

        private const val USER_API_PATH = "api/user/"

        private const val DELETE_MESSAGES_PATH = "messages/delete/"
        private const val MARK_READ_MESSAGES_PATH = "messages/unread/"
        private const val MESSAGES_PATH = "messages/"

        private const val MESSAGES_REPORTINGS_KEY = "messages"
        private const val CHANNEL_ID_HEADER = "X-UA-Channel-ID"

        private const val PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels"
        private const val PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels"
        private const val PAYLOAD_ADD_KEY = "add"
    }
}
