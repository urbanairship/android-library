/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import java.time.Instant

internal data class ContactIdUpdate(
    val contactId: String,
    val namedUserId: String?,
    val isStable: Boolean,
    val resolveDate: Instant) {

    fun toContactInfo(): StableContactInfo = StableContactInfo(contactId, namedUserId)
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class StableContactInfo(
    public val contactId: String,
    public val namedUserId: String?
)
