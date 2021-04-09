package com.urbanairship.chat

import android.net.TrafficStats
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UAirship
import com.urbanairship.config.UrlBuilder
import com.urbanairship.http.RequestFactory
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.UAHttpStatusUtil
import java.io.IOException
import java.net.URL

/**
 * API Client for interacting with Chat REST endpoints.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ChatApiClient
/**
 * Full constructor (for tests).
 *
 * @hide
 */
@VisibleForTesting constructor(
    private val requestFactory: RequestFactory
) {

    companion object {
        private val UVP_URL_BASE =
                UrlBuilder("https://wwrni3iy87.execute-api.us-west-1.amazonaws.com/Prod/api/UVP")
        private const val ANDROID_PLATFORM = "Android"
        private const val AMAZON_PLATFORM = "Amazon"
    }

    private val appKey by lazy {
        UAirship.shared().airshipConfigOptions.appKey
    }

    private val platformType by lazy {
        when (UAirship.shared().platformType) {
            UAirship.ANDROID_PLATFORM -> ANDROID_PLATFORM
            else -> AMAZON_PLATFORM
        }
    }

    /** Default constructor. */
    constructor() : this(requestFactory = RequestFactory.DEFAULT_REQUEST_FACTORY)

    fun fetchUvp(channelId: String): String {
        // Avoid UntaggedSocketViolations
        TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())

        return requestFactory.createRequest("GET", buildUvpUrl(channelId))
                .execute { status, _, responseBody ->
                    if (!UAHttpStatusUtil.inSuccessRange(status)) {
                        throw IOException("Failed to fetch UVP! (status: $status)")
                    }
                    val body = responseBody
                            ?: throw IOException("Failed to fetch UVP! Response body was null.")

                    JsonValue.parseString(body).optMap().opt("uvp").string
                            ?: throw JsonException("Invalid response, uvp is null!")
                }.result
    }

    private fun buildUvpUrl(channelId: String): URL =
        UVP_URL_BASE
            .appendQueryParameter("appKey", appKey)
            .appendQueryParameter("channelId", channelId)
            .appendQueryParameter("platform", platformType)
            .build() ?: throw IllegalStateException("UVP URL is malformed!")
}
