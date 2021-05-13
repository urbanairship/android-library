/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import com.urbanairship.UAirship
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestFactory
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.PlatformUtils
import com.urbanairship.util.UAHttpStatusUtil
import java.io.IOException

/**
 * API Client for interacting with Chat REST endpoints.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ChatApiClient(private val config: AirshipRuntimeConfig, private val requestFactory: RequestFactory = RequestFactory.DEFAULT_REQUEST_FACTORY) {

    fun fetchUvp(channelId: String): String {
        val platform = config.platform
        if (platform == UAirship.UNKNOWN_PLATFORM) {
            throw IllegalStateException("Invalid platform")
        }

        val url = config.urlConfig.chatUrl()
                .appendEncodedPath("api/UVP")
                .appendQueryParameter("appKey", config.configOptions.appKey)
                .appendQueryParameter("channelId", channelId)
                .appendQueryParameter("platform", PlatformUtils.asString(platform).capitalize())
                .build()

        return requestFactory.createRequest().setOperation("GET", url)
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
}
