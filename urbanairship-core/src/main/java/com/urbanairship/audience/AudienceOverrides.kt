/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.contacts.ScopedSubscriptionListMutation

/**
 * Audience overrides.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class AudienceOverrides {
    public data class Contact(
        val tags: List<TagGroupsMutation>? = null,
        val attributes: List<AttributeMutation>? = null,
        val subscriptions: List<ScopedSubscriptionListMutation>? = null
    ) : AudienceOverrides()

    public data class Channel(
        val tags: List<TagGroupsMutation>? = null,
        val attributes: List<AttributeMutation>? = null,
        val subscriptions: List<SubscriptionListMutation>? = null
    ) : AudienceOverrides()
}
