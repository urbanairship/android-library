/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import androidx.annotation.RestrictTo

internal data class ContactIdUpdate(
    val contactId: String,
    val namedUserId: String?,
    val isStable: Boolean,
    val resolveDateMs: Long) {

    fun toContactInfo(): StableContactInfo = StableContactInfo(contactId, namedUserId)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class StableContactInfo(
    public val contactId: String,
    public val namedUserId: String?
)
