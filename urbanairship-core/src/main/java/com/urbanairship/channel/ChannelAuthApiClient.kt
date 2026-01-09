/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.net.Uri
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthToken
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import com.urbanairship.http.RequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock
import com.urbanairship.util.UAHttpStatusUtil

internal class ChannelAuthApiClient(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val requestSession: RequestSession = runtimeConfig.requestSession,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) {
    suspend fun getToken(channelId: String): RequestResult<AuthToken> {
        val url: Uri? = runtimeConfig.deviceUrl
            .appendEncodedPath("api/auth/device")
            .build()

        val requestTime = clock.currentTimeMillis()

        val request = Request(
            url = url,
            method = "GET",
            auth = RequestAuth.GeneratedChannelToken(channelId),
        )

        return requestSession.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return@execute null
            }

            JsonValue.parseString(responseBody)
                .requireMap()
                .let { map ->
                    AuthToken(
                        identifier = channelId,
                        token = map.require("token").requireString(),
                        expirationDateMillis = requestTime + map.require("expires_in").getLong(0)
                    )
                }
        }
    }
}
