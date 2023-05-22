/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.api

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.urbanairship.Logger
import com.urbanairship.UAirship.AMAZON_PLATFORM
import com.urbanairship.UAirship.ANDROID_PLATFORM
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.liveupdate.util.jsonMapOf
import com.urbanairship.liveupdate.util.requireField
import com.urbanairship.liveupdate.util.toJsonList
import com.urbanairship.util.Clock

/** API client for the channel bulk update endpoint. */
internal class ChannelBulkUpdateApiClient(
    private val config: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = config.requestSession.toSuspendingRequestSession()
) {
    /** Bulk update channel subscription lists, tags, and attributes. */
    @Throws(RequestException::class)
    suspend fun update(
        channelId: String,
        tags: List<TagGroupsMutation>? = null,
        attributes: List<AttributeMutation>? = null,
        subscriptions: List<SubscriptionListMutation>? = null,
        liveUpdates: List<LiveUpdateMutation>? = null,
    ): RequestResult<Unit> {
        val payload =
            ChannelBulkUpdateRequest(channelId, tags, attributes, subscriptions, liveUpdates)
        Logger.verbose("Bulk updating channel ($channelId) with payload: ${payload.toJsonValue()}")

        val request = Request(
            url = bulkUpdateUrl(channelId),
            method = "PUT",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;"
            )
        )

        return session.execute(request).also { response ->
            Logger.v { "Bulk finished with response $response" }
        }
    }

    private fun bulkUpdateUrl(channelId: String): Uri? {
        val builder = config.urlConfig.deviceUrl()
            .appendEncodedPath(CHANNEL_BULK_UPDATE_PATH)
            .appendPath(channelId)

        platform?.let {
            builder.appendQueryParameter(PLATFORM_PARAM, it)
        }

        return builder.build()
    }

    private val platform = when (config.platform) {
        ANDROID_PLATFORM -> PLATFORM_ANDROID
        AMAZON_PLATFORM -> PLATFORM_AMAZON
        else -> null
    }

    private companion object {
        private const val CHANNEL_BULK_UPDATE_PATH = "api/channels/sdk/batch"
        private const val PLATFORM_ANDROID = "android"
        private const val PLATFORM_AMAZON = "amazon"
        private const val PLATFORM_PARAM = "platform"
    }
}

@VisibleForTesting
internal data class ChannelBulkUpdateRequest @JvmOverloads constructor (
    val channelId: String,
    val tagGroups: List<TagGroupsMutation>? = null,
    val attributes: List<AttributeMutation>? = null,
    val subscriptionLists: List<SubscriptionListMutation>? = null,
    val liveUpdates: List<LiveUpdateMutation>? = null
) : JsonSerializable {
    override fun toJsonValue(): JsonValue = JsonMap.newBuilder().apply {
        tagGroups?.let { tags ->
            put(TAGS, TagGroupsMutation.collapseMutations(tags).toJsonList())
        }
        attributes?.let { attrs ->
            put(ATTRIBUTES, AttributeMutation.collapseMutations(attrs).toJsonList())
        }
        subscriptionLists?.let { lists ->
            put(SUBSCRIPTION_LISTS, SubscriptionListMutation.collapseMutations(lists).toJsonList())
        }
        liveUpdates?.let { updates ->
            put(LIVE_UPDATES, updates.map { it.toJsonValue() }.toJsonList())
        }
    }.build().toJsonValue()

    private companion object {
        private const val TAGS = "tags"
        private const val ATTRIBUTES = "attributes"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
        private const val LIVE_UPDATES = "live_updates"
    }
}

internal sealed class LiveUpdateMutation(
    val action: String,
) : JsonSerializable {
    abstract val name: String
    abstract val startTime: Long
    abstract val actionTime: Long

    internal class Set(
        override val name: String,
        override val startTime: Long,
        override val actionTime: Long = Clock.DEFAULT_CLOCK.currentTimeMillis()

    ) : LiveUpdateMutation(ACTION_SET)
    internal class Remove(
        override val name: String,
        override val startTime: Long,
        override val actionTime: Long = Clock.DEFAULT_CLOCK.currentTimeMillis()
    ) : LiveUpdateMutation(ACTION_REMOVE)

    override fun toJsonValue(): JsonValue =
        jsonMapOf(
            KEY_ACTION to action,
            KEY_NAME to name,
            KEY_START_TS to startTime,
            KEY_ACTION_TS to actionTime
        ).toJsonValue()

    internal companion object {
        private const val KEY_ACTION = "action"
        private const val KEY_NAME = "name"
        private const val KEY_START_TS = "start_ts_ms"
        private const val KEY_ACTION_TS = "action_ts_ms"
        private const val ACTION_SET = "set"
        private const val ACTION_REMOVE = "remove"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): LiveUpdateMutation {
            val action: String = json.requireField(KEY_ACTION)
            val name: String = json.requireField(KEY_NAME)
            val startTime: Long = json.requireField(KEY_START_TS)
            val actionTime: Long = json.requireField(KEY_ACTION_TS)

            return when (action) {
                ACTION_SET -> Set(name, startTime, actionTime)
                ACTION_REMOVE -> Remove(name, startTime, actionTime)
                else -> throw JsonException("Failed to parse LiveUpdateMutation json: $json")
            }
        }
    }
}
