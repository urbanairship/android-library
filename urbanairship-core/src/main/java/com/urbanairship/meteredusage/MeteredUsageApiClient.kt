package com.urbanairship.meteredusage

import com.urbanairship.UAirship
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.log
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.jsonMapOf
import java.security.InvalidParameterException

internal class MeteredUsageApiClient(
    private val config: AirshipRuntimeConfig,
    private var session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
) {

    @Throws(InvalidParameterException::class)
    suspend fun uploadEvents(
        events: List<MeteredUsageEventEntity>,
        channelId: String?
    ): RequestResult<Unit> {
        val meteredUsageUrl =
            config.urlConfig.meteredUsageUrl().appendEncodedPath("api/metered-usage").build()
                ?: throw InvalidParameterException("Missing metered usage URL")

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

        channelId?.let { headers["X-UA-Channel-ID"] = it }

        val request = Request(
            url = meteredUsageUrl,
            method = "POST",
            headers = headers.toMap(),
            body = RequestBody.Json(jsonMapOf("usage" to events.map { it.toJson() })),
            auth = RequestAuth.GeneratedAppToken
        )

        return session.execute(request).also { result ->
            result.log { "Usage result: $result" }
        }
    }
}
