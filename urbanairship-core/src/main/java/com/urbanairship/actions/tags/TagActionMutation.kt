package com.urbanairship.actions.tags

public sealed class TagActionMutation {
    public data class ChannelTags(val tags: Set<String>) : TagActionMutation()
    public data class ChannelTagGroups(val tags: Map<String, Set<String>>) : TagActionMutation()
    public data class ContactTagGroups(val tags: Map<String, Set<String>>) : TagActionMutation()
}