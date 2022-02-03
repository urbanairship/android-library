/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import androidx.annotation.RestrictTo;

/**
 * Channel types.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum ChannelType {
    /**
     * Open channel
     */
    OPEN,
    /**
     * Sms channel
     */
    SMS,
    /**
     * Email channel
     */
    EMAIL
}
