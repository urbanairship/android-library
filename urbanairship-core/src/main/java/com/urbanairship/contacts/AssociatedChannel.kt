/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

/**
 * A contact associated channel.
 */
public class AssociatedChannel(
    /**
     * The channel ID.
     *
     * @return The channel ID.
     */
    public val channelId: String,
    /**
     * The channel type.
     *
     * @return The channel type.
     */
    public val channelType: ChannelType
) : JsonSerializable {
    @Throws(JsonException::class)
    internal constructor(json: JsonValue) : this(
        channelId = json.requireMap().requireField(CHANNEL_ID_KEY),
        channelType = ChannelType.valueOf(json.requireMap().requireField(CHANNEL_TYPE_KEY))
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CHANNEL_TYPE_KEY to channelType.toString(),
        CHANNEL_ID_KEY to channelId
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return if (other is AssociatedChannel) {
            channelId == other.channelId && channelType == other.channelType
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(channelId, channelType)
    }

    override fun toString(): String {
        return "AssociatedChannel(channelId='$channelId', channelType=$channelType)"
    }

    internal companion object {
        private const val CHANNEL_ID_KEY = "channel_id"
        private const val CHANNEL_TYPE_KEY = "channel_type"
    }
}
