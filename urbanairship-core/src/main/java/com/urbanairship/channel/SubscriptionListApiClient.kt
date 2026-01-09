/* Copyright Airship and Contributors */

package com.urbanairship.channel

import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import com.urbanairship.http.RequestSession
import com.urbanairship.http.log
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList
import com.urbanairship.util.UAHttpStatusUtil

@OpenForTesting
internal class SubscriptionListApiClient(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: RequestSession = runtimeConfig.requestSession
) {
    suspend fun getSubscriptionLists(channelId: String): RequestResult<Set<String>> {
        val url = runtimeConfig.deviceUrl
            .appendEncodedPath("api/subscription_lists/channels/$channelId")
            .build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
        )

        val request = Request(
            url = url,
            method = "GET",
            auth = RequestAuth.ChannelTokenAuth(channelId),
            headers = headers
        )

        UALog.d { "Fetching contact subscription lists for $channelId request: $request" }

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return@execute null
            }

            return@execute JsonValue.parseString(responseBody)
                .requireMap()
                .optionalList("list_ids")
                ?.map { value -> value.requireString() }
                ?.toSet()
                ?: emptySet()
        }.also { result ->
            result.log { "Fetching contact subscription lists for $channelId finished with result: $result" }
        }
    }
}
