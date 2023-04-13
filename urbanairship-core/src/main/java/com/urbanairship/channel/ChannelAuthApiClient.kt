/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.net.Uri
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthToken
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestSession
import com.urbanairship.http.Response
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.UAHttpStatusUtil
import com.urbanairship.util.UAStringUtil
import java.util.UUID

internal class ChannelAuthApiClient(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val requestSession: RequestSession = runtimeConfig.requestSession,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val nonceTokenFactory: () -> String = { UUID.randomUUID().toString() }
) {
    @Throws(RequestException::class)
    fun getToken(channelId: String): Response<AuthToken?> {
        val url: Uri? = runtimeConfig.urlConfig
            .deviceUrl()
            .appendEncodedPath("api/auth/device")
            .build()

        val requestTime = clock.currentTimeMillis()
        val nonce = nonceTokenFactory()
        val timestamp = DateUtils.createIso8601TimeStamp(requestTime)

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "X-UA-Channel-ID" to channelId,
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey,
            "X-UA-Nonce" to nonce,
            "X-UA-Timestamp" to timestamp
        )

        val token = try {
            UAStringUtil.generateSignedToken(
                runtimeConfig.configOptions.appSecret,
                listOf(
                    runtimeConfig.configOptions.appKey, channelId, nonce, timestamp
                )
            )
        } catch (e: Exception) {
            throw RequestException("Unable to generate token", e)
        }

        val request = Request(
            url = url,
            method = "GET",
            auth = RequestAuth.BearerToken(token),
            headers = headers
        )

        return requestSession.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                JsonValue.parseString(responseBody).requireMap().let { map ->
                    AuthToken(
                        identifier = channelId,
                        token = map.require("token").requireString(),
                        expirationTimeMS = requestTime + map.require("expires_in").getLong(0)
                    )
                }
            } else {
                null
            }
        }
    }
}
