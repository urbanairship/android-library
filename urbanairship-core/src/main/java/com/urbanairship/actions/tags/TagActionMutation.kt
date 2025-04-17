/* Copyright Airship and Contributors */

package com.urbanairship.actions.tags

/** Tag mutations from [AddTagsAction] and [RemoveTagsAction] **/
public sealed class TagActionMutation {
    /**
     * Represents a mutation for applying a set of tags to a channel.
     *
     * @param tags A set of unique strings representing the tags to be applied to the channel.
     */
    public data class ChannelTags(val tags: Set<String>) : TagActionMutation()

    /**
     * Represents a mutation for applying tag group changes to the channel.
     *
     * @param tags A map of tag group to tags.
     */
    public data class ChannelTagGroups(val tags: Map<String, Set<String>>) : TagActionMutation()

    /**
     * Represents a mutation for applying tag group changes to the contact.
     *
     * @param tags A map of tag group to tags.
     */
    public data class ContactTagGroups(val tags: Map<String, Set<String>>) : TagActionMutation()
}