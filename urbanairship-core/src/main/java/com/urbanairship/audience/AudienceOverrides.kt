/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.contacts.ContactChannelMutation
import com.urbanairship.contacts.ScopedSubscriptionListMutation

internal sealed class AudienceOverrides {
    data class Contact(
        val tags: List<TagGroupsMutation>? = null,
        val attributes: List<AttributeMutation>? = null,
        val subscriptions: List<ScopedSubscriptionListMutation>? = null,
        val channels: List<ContactChannelMutation>? = null
    ) : AudienceOverrides()

    data class Channel(
        val tags: List<TagGroupsMutation>? = null,
        val attributes: List<AttributeMutation>? = null,
        val subscriptions: List<SubscriptionListMutation>? = null
    ) : AudienceOverrides()
}
