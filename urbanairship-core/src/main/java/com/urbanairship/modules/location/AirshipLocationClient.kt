/* Copyright Airship and Contributors */
package com.urbanairship.modules.location

import androidx.annotation.RestrictTo

/**
 * Airship Location interface.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipLocationClient {

    /**
     * Enable or disable continuous location updates.
     *
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     */
    public var isLocationUpdatesEnabled: Boolean

    /**
     * Enable or disable allowing continuous updates to continue in
     * the background.
     */
    public var isBackgroundLocationAllowed: Boolean

    /**
     * Returns `true` if location is permitted and the location manager updates are enabled, otherwise `false`.
     * @return <`true` if location is permitted and the location manager updates are enabled, otherwise `false`.
     */
    public val isOptIn: Boolean
}
