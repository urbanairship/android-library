package com.urbanairship.contacts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Contact conflict listener.
 */
public interface ContactConflictListener {

    /**
     * Called when an anonymous user data will be lost due to the device being associated to an existing contact or
     * when the device is associated to a contact outside of the SDK.
     *
     * @param anonymousContactData The anonymous contact data.
     * @param namedUserId The named user ID. Will be null if the conflict happens outside of the SDK.
     */
    void onConflict(@NonNull ContactData anonymousContactData, @Nullable String namedUserId);
}
