/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import com.urbanairship.UAirship
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.config.UrlBuilder
import com.urbanairship.http.RequestFactory
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.PlatformUtils
import com.urbanairship.util.UAHttpStatusUtil
import java.io.IOException
import java.net.URL

/**
 * API Client for interacting with Chat REST endpoints.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ChatApiClient(
    private val config: AirshipRuntimeConfig,
    private val requestFactory: RequestFactory = RequestFactory.DEFAULT_REQUEST_FACTORY
) {

    companion object {
        private const val UVP_URL_BASE = "https://ny4uaaegbg.execute-api.us-west-1.amazonaws.com/Prod/"
    }

    fun fetchUvp(channelId: String): String {
        return requestFactory.createRequest()
                .setOperation("GET", buildUvpUrl(channelId))
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

    private fun buildUvpUrl(channelId: String): URL? {
        val platformType = PlatformUtils.asString(UAirship.shared().platformType).capitalize()
        return UrlBuilder(UVP_URL_BASE)
                .appendEncodedPath("api/UVP")
                .appendQueryParameter("appKey", config.configOptions.appKey)
                .appendQueryParameter("channelId", channelId)
                .appendQueryParameter("platform", platformType)
                .build()
    }
}
