/* Copyright Airship and Contributors */

package com.urbanairship.channel

import com.urbanairship.Logger
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.log
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.util.UAHttpStatusUtil

@OpenForTesting
internal class SubscriptionListApiClient constructor(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = runtimeConfig.requestSession.toSuspendingRequestSession()
) {
    suspend fun getSubscriptionLists(channelId: String): RequestResult<Set<String>> {
        val url = runtimeConfig.urlConfig.deviceUrl()
            .appendEncodedPath("api/subscription_lists/channels/$channelId")
            .build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
        )

        val request = Request(
            url = url,
            method = "GET",
            auth = RequestAuth.BasicAppAuth,
            headers = headers
        )

        Logger.d { "Fetching contact subscription lists for $channelId request: $request" }

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            return@execute if (UAHttpStatusUtil.inSuccessRange(status)) {
                JsonValue.parseString(responseBody).requireMap()
                    .optionalField<JsonList>("list_ids")
                    ?.map { value -> value.requireString() }
                    ?.toSet() ?: emptySet<String>()
            } else {
                null
            }
        }.also { result ->
            result.log { "Fetching contact subscription lists for $channelId finished with result: $result" }
        }
    }
}
