/* Copyright Airship and Contributors */

package com.urbanairship.modules.location;

/**
 * Airship Location interface.
 */
public interface AirshipLocationClient {

    /**
     * Checks if continuous location updates is enabled or not.
     * <p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @return <code>true</code> if location updates are enabled, otherwise
     * <code>false</code>.
     */
    boolean isLocationUpdatesEnabled();

    /**
     * Enable or disable continuous location updates.
     * <p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @param enabled If location updates should be enabled or not.
     */
    void setLocationUpdatesEnabled(boolean enabled);

    /**
     * Checks if continuous location updates are allowed to continue
     * when the application is in the background.
     *
     * @return <code>true</code> if continuous location update are allowed in the background,
     * otherwise <code>false</code>.
     */
    boolean isBackgroundLocationAllowed();

    /**
     * Enable or disable allowing continuous updates to continue in
     * the background.
     *
     * @param enabled If background updates are allowed in the background or not.
     */
    void setBackgroundLocationAllowed(boolean enabled);

    /**
     * Returns {@code true} if location is permitted and the location manager updates are enabled, otherwise {@code false}.
     *
     * @return <{@code true} if location is permitted and the location manager updates are enabled, otherwise {@code false}.
     */
    boolean isOptIn();
}
