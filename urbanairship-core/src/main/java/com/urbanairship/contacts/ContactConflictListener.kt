/* Copyright Airship and Contributors */
package com.urbanairship.contacts

/**
 * Contact conflict listener.
 */
@FunctionalInterface
public interface ContactConflictListener {

    /**
     * Called when an anonymous user data will be lost due to the device being associated to an existing contact or
     * when the device is associated to a contact outside of the SDK.
     *
     * @param event The conflict event.
     */
    public fun onConflict(event: ConflictEvent)
}
