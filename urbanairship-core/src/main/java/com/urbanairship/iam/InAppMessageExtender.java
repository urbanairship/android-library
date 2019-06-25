/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;

/**
 * Interface used to extend in-app messages.
 */
public interface InAppMessageExtender {

    /**
     * Called to extend the in-app message.
     *
     * @param message The original message.
     * @return The extended message.
     */
    @NonNull
    InAppMessage extend(@NonNull InAppMessage message);

}
