/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.net.Uri
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils

/**
 * API client for fetching remote data.
 */
internal class RemoteDataApiClient @JvmOverloads constructor(
    private val config: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
) {
    internal data class Result(
        val remoteDataInfo: RemoteDataInfo,
        val payloads: Set<RemoteDataPayload>
    )

    internal suspend fun fetch(
        remoteDataUrl: Uri?,
        auth: RequestAuth,
        lastModified: String?,
        remoteDataInfoFactory: (String?) -> RemoteDataInfo
    ): RequestResult<Result> {
        val headers = mutableMapOf(
            "X-UA-Appkey" to config.configOptions.appKey
        )

        lastModified?.let {
            headers["If-Modified-Since"] = it
        }

        val request = Request(
            remoteDataUrl, "GET", auth, null, headers
        )

        return session.execute(request) { status: Int, responseHeaders: Map<String, String>, responseBody: String? ->
            return@execute if (status == 200) {
                val remoteDataInfo = remoteDataInfoFactory(responseHeaders["Last-Modified"])
                val payloads = parseResponse(responseBody, remoteDataInfo)
                Result(
                    remoteDataInfo,
                    payloads
                )
            } else {
                null
            }
        }
    }

    @Throws(JsonException::class)
    private fun parsePayload(json: JsonValue, remoteDataInfo: RemoteDataInfo): RemoteDataPayload {
        val map = json.requireMap()
        return RemoteDataPayload(
            type = map.requireField("type"),
            timestamp = map.requireField<String>("timestamp").let {
                try {
                    DateUtils.parseIso8601(it)
                } catch (e: Exception) {
                    throw JsonException("Invalid timestamp $it", e)
                }
            },
            data = map.optionalField<JsonMap>("data") ?: JsonMap.EMPTY_MAP,
            remoteDataInfo = remoteDataInfo
        )
    }

    @Throws(JsonException::class)
    private fun parseResponse(responseBody: String?, remoteDataInfo: RemoteDataInfo): Set<RemoteDataPayload> {
        if (responseBody == null) {
            return emptySet()
        }
        val payloadJsonList = JsonValue.parseString(responseBody).optMap().opt("payloads").optList()
        return payloadJsonList.map {
            parsePayload(it, remoteDataInfo)
        }.toSet()
    }
}
