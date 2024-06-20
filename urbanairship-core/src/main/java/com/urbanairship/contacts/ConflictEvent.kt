/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonValue

/**
 * Conflict event is generated if an anonymous contact with tags, subscriptions, attributes,
 * or associated channels is going to be dropped due to a contact change. The app can listen for
 * this event to migrate the data.
 */
public class ConflictEvent(
    /**
     * Contact tag groups.
     */
    public val tagGroups: Map<String, Set<String>> = emptyMap(),

    /**
     * Contact attributes.
     */
    public val attributes: Map<String, JsonValue> = emptyMap(),

    /**
     * Contact subscription lists.
     */
    public val subscriptionLists: Map<String, Set<Scope>> = emptyMap(),

    /**
     * A list of associated contacts.
     */
    public val associatedChannels: List<ChannelInfo> = emptyList(),

    /**
     * The named user ID if the conflict was caused by an identify operation
     */
    public val conflictingNameUserId: String? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ConflictEvent) {
            if (tagGroups != other.tagGroups) return false
            if (attributes != other.attributes) return false
            if (subscriptionLists != other.subscriptionLists) return false
            if (associatedChannels != other.associatedChannels) return false
            if (conflictingNameUserId != other.conflictingNameUserId) return false
            return true
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(tagGroups, attributes, subscriptionLists, associatedChannels, conflictingNameUserId)
    }

    override fun toString(): String {
        return "ConflictEvent(tagGroups=$tagGroups, attributes=$attributes, subscriptionLists=$subscriptionLists, associatedChannels=$associatedChannels, conflictingNameUserId=$conflictingNameUserId)"
    }

    /**
     * Channel Info.
     */
    public class ChannelInfo(
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
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ChannelInfo

            if (channelId != other.channelId) return false
            if (channelType != other.channelType) return false

            return true
        }

        override fun hashCode(): Int = ObjectsCompat.hash(channelId, channelType)
    }
}
