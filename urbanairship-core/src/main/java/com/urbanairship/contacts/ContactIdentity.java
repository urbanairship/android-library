/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * Model object for Contact identity.
 */
class ContactIdentity implements JsonSerializable {

    private static final String CONTACT_ID_KEY = "contact_id";
    private static final String IS_ANONYMOUS_KEY = "is_anonymous";
    private static final String NAMED_USER_ID_KEY = "named_user_id";

    /**
     * The contact ID
     */
    @NonNull
    private final String contactId;

    /**
     * The status of the contact (named or anonymous)
     */
    private final boolean isAnonymous;

    @Nullable
    private final String namedUserId;

    ContactIdentity(@NonNull String contactId, boolean isAnonymous, @Nullable String namedUserId) {
        this.contactId = contactId;
        this.isAnonymous = isAnonymous;
        this.namedUserId = namedUserId;
    }

    @NonNull
    public String getContactId() {
        return contactId;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }

    @Nullable
    public String getNamedUserId() {
        return namedUserId;
    }

    @NonNull
    static ContactIdentity fromJson(@NonNull JsonValue value) throws JsonException {
        String contactId = value.optMap().opt(CONTACT_ID_KEY).getString();
        if (contactId == null) {
            throw new JsonException("Invalid contact identity " + value);
        }

        String namedUserId = value.optMap().opt(NAMED_USER_ID_KEY).getString();
        boolean isAnonymous = value.optMap().opt(IS_ANONYMOUS_KEY).getBoolean(false);
        return new ContactIdentity(contactId, isAnonymous, namedUserId);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(CONTACT_ID_KEY, contactId)
                      .put(IS_ANONYMOUS_KEY, isAnonymous)
                      .put(NAMED_USER_ID_KEY, namedUserId)
                      .build().toJsonValue();
    }

}
