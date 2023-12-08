/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/**
 * Model object for Contact identity.
 */
@OpenForTesting
internal data class ContactIdentity(
    /**
     * The contact ID
     */
    val contactId: String,
    val isAnonymous: Boolean,
    val namedUserId: String?,
    val resolveDateMs: Long?
) : JsonSerializable {
    constructor(jsonValue: JsonValue) : this(
            contactId = jsonValue.requireMap().requireField(CONTACT_ID_KEY),
            isAnonymous = jsonValue.requireMap().optionalField(IS_ANONYMOUS_KEY) ?: false,
            namedUserId = jsonValue.requireMap().optionalField(NAMED_USER_ID_KEY),
            resolveDateMs = jsonValue.requireMap().optionalField(RESOLVE_DATE_KEY)
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
            CONTACT_ID_KEY to contactId,
            IS_ANONYMOUS_KEY to isAnonymous,
            NAMED_USER_ID_KEY to namedUserId,
            RESOLVE_DATE_KEY to resolveDateMs
    ).toJsonValue()

    companion object {
        private const val CONTACT_ID_KEY = "contact_id"
        private const val IS_ANONYMOUS_KEY = "is_anonymous"
        private const val NAMED_USER_ID_KEY = "named_user_id"
        private const val RESOLVE_DATE_KEY = "resolve_date_ms"
    }
}
