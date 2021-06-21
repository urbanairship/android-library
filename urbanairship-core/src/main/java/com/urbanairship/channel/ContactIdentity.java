/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;

/**
 * Model object for Contact identity.
 */
class ContactIdentity {

    /**
     * The contact ID
     */
    @NonNull
    private final String contactId;

    /**
     * The status of the contact (named or anonymous)
     */
    private final boolean isAnonymous;

     ContactIdentity(@NonNull String contactId, boolean isAnonymous) {
        this.contactId = contactId;
        this.isAnonymous = isAnonymous;
    }

    @NonNull
    public String getContactId() {
        return contactId;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }
}
