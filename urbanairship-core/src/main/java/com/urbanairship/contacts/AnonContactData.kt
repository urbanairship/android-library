/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

internal data class AnonContactData internal constructor(
    val tagGroups: Map<String, Set<String>> = emptyMap(),
    val attributes: Map<String, JsonValue> = emptyMap(),
    val subscriptionLists: Map<String, Set<Scope>> = emptyMap(),
    val associatedChannels: List<AnonChannel> = emptyList()
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        TAG_GROUPS_KEY to tagGroups,
        ATTRIBUTES_KEY to attributes,
        SUBSCRIPTION_LISTS to subscriptionLists,
        ASSOCIATED_CHANNELS_KEY to associatedChannels
    ).toJsonValue()

    internal val isEmpty: Boolean
        get() = attributes.isEmpty() && tagGroups.isEmpty() && associatedChannels.isEmpty() && subscriptionLists.isEmpty()



    internal companion object {
        private const val TAG_GROUPS_KEY = "tag_groups"
        private const val ATTRIBUTES_KEY = "attributes"
        private const val ASSOCIATED_CHANNELS_KEY = "associated_channels"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
        @Throws(JsonException::class)
        fun fromJson(jsonValue: JsonValue) : AnonContactData {
            return AnonContactData(
                tagGroups = jsonValue.optMap().opt(TAG_GROUPS_KEY).optMap().map.mapValues { entry ->
                    entry.value.optList().mapNotNull { it.string }.toSet()
                },
                attributes = jsonValue.optMap().opt(ATTRIBUTES_KEY).optMap().map,
                subscriptionLists = jsonValue.optMap().opt(SUBSCRIPTION_LISTS).optMap().map.mapValues { entry ->
                    entry.value.optList().mapNotNull { Scope.fromJson(it) }.toSet()
                },
                associatedChannels = jsonValue.optMap().opt(ASSOCIATED_CHANNELS_KEY).optList().mapNotNull {
                    AnonChannel.fromJson(it)
                }
            )
        }
    }
}

internal data class AnonChannel(
    val channelId: String,
    val channelType: ChannelType
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CHANNEL_TYPE_KEY to channelType.toString(),
        CHANNEL_ID_KEY to channelId
    ).toJsonValue()

    internal companion object {
        private const val CHANNEL_ID_KEY = "channel_id"
        private const val CHANNEL_TYPE_KEY = "channel_type"

        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): AnonChannel {
            return AnonChannel(
                channelId = json.requireMap().requireField(CHANNEL_ID_KEY),
                channelType = ChannelType.fromJson(json.requireMap().requireField(CHANNEL_TYPE_KEY))
            )
        }
    }
}
