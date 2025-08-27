/* Copyright Airship and Contributors */

package com.urbanairship.channel

import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf

/** API client for the channel bulk update endpoint. */
internal class ChannelBatchUpdateApiClient(
    private val config: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
) {
    /** Bulk update channel subscription lists, tags, and attributes. */
    suspend fun update(
        channelId: String,
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<SubscriptionListMutation>? = null,
        liveUpdates: List<LiveUpdateMutation>? = null
    ): RequestResult<Unit> {

        val payload = jsonMapOf(
            TAGS to tags?.ifEmpty { null }?.tagsPayload(),
            ATTRIBUTES to attributes?.ifEmpty { null },
            SUBSCRIPTION_LISTS to subscriptions?.ifEmpty { null },
            LIVE_UPDATES to liveUpdates?.ifEmpty { null }
        )

        UALog.v { "Bulk updating channel ($channelId) with payload: $payload" }

        val request = Request(
            url = bulkUpdateUrl(channelId),
            method = "PUT",
            auth = RequestAuth.ChannelTokenAuth(channelId),
            body = RequestBody.Json(payload),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;"
            )
        )

        // Execute the request and log the result at debug level
        val result = session.execute(request)
        UALog.d { "Updating channel finished with result: $result" }
        return result
    }

    private fun bulkUpdateUrl(channelId: String): Uri? {
        val builder = config.deviceUrl
            .appendEncodedPath(CHANNEL_BULK_UPDATE_PATH)
            .appendPath(channelId)

        val platformName = when (config.platform) {
            Airship.Platform.ANDROID -> PLATFORM_ANDROID
            Airship.Platform.AMAZON -> PLATFORM_AMAZON
            else -> null
        }

        platformName?.let {
            builder.appendQueryParameter(PLATFORM_PARAM, it)
        }

        return builder.build()
    }

    private companion object {
        private const val CHANNEL_BULK_UPDATE_PATH = "api/channels/sdk/batch"
        private const val PLATFORM_ANDROID = "android"
        private const val PLATFORM_AMAZON = "amazon"
        private const val PLATFORM_PARAM = "platform"

        private const val TAGS = "tags"
        private const val ATTRIBUTES = "attributes"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
        private const val LIVE_UPDATES = "live_updates"
    }
}

private fun List<TagGroupsMutation>.tagsPayload(): JsonMap? {
    val add = mutableMapOf<String, MutableSet<String>>()
    val remove = mutableMapOf<String, MutableSet<String>>()
    val set = mutableMapOf<String, MutableSet<String>>()

    this.forEach { mutation ->
        mutation.addTags.forEach { entry ->
            add.getOrPut(entry.key) { mutableSetOf() }.addAll(entry.value)
        }
        mutation.removeTags.forEach { entry ->
            remove.getOrPut(entry.key) { mutableSetOf() }.addAll(entry.value)
        }
        mutation.setTags.forEach { entry ->
            set.getOrPut(entry.key) { mutableSetOf() }.addAll(entry.value)
        }
    }

    if (add.isEmpty() && remove.isEmpty() && set.isEmpty()) {
        return null
    }

    return jsonMapOf(
        "add" to add.ifEmpty { null },
        "remove" to remove.ifEmpty { null },
        "set" to set.ifEmpty { null }
    )
}
