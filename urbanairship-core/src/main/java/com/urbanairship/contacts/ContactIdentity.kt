/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

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
    val namedUserId: String?
) : JsonSerializable {
    constructor(jsonValue: JsonValue) : this(
        jsonValue.requireMap().require(CONTACT_ID_KEY).requireString(),
        jsonValue.requireMap().opt(IS_ANONYMOUS_KEY).getBoolean(false),
        jsonValue.requireMap().opt(NAMED_USER_ID_KEY).optString()
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CONTACT_ID_KEY to contactId,
        IS_ANONYMOUS_KEY to isAnonymous,
        NAMED_USER_ID_KEY to namedUserId
    ).toJsonValue()

    companion object {
        private const val CONTACT_ID_KEY = "contact_id"
        private const val IS_ANONYMOUS_KEY = "is_anonymous"
        private const val NAMED_USER_ID_KEY = "named_user_id"
    }
}
