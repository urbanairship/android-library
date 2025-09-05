/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

import androidx.annotation.Size
import com.urbanairship.UALog
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth.ChannelTokenAuth
import com.urbanairship.http.RequestBody.GzippedJson
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestSession
import com.urbanairship.http.Response
import com.urbanairship.json.JsonValue
import java.util.Locale

/** A client that handles uploading analytic events */
internal class EventApiClient(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: RequestSession = runtimeConfig.requestSession
) {

    /**
     * Sends a collection of events.
     *
     * @param channelId The channel Id
     * @param events Specified events
     * @param headers Headers
     * @return eventResponse
     */
    @Throws(RequestException::class)
    fun sendEvents(
        channelId: String,
        events: List<JsonValue?>,
        @Size(min = 1) headers: Map<String, String>
    ): Response<EventResponse> {
        val sentAt = System.currentTimeMillis() / 1000.0
        val requestHeaders = headers
            .toMutableMap()
            .apply { put("X-UA-Sent-At", String.format(Locale.US, "%.3f", sentAt)) }
            .toMap()

        val url = runtimeConfig.analyticsUrl.appendEncodedPath(WARP9_PATH).build()

        val request = Request(
            url,
            "POST",
            ChannelTokenAuth(channelId),
            GzippedJson(JsonValue.wrapOpt(events)),
            requestHeaders
        )

        UALog.d("Sending analytics events. Request: $request Events: $events")
        val response = session.execute(request) { _, responseHeaders, _ ->
            EventResponse(responseHeaders)
        }

        UALog.d("Analytics event response: $response")
        return response
    }

    internal companion object {
        private const val WARP9_PATH = "warp9/"
    }
}
