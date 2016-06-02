/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

/**
 * Base class for Urban Airship components.
 */
public abstract class AirshipComponent {

    /*
     * The only reason for this class is to hide the init and tearDown methods.
     */

    /**
     * Initialize the manager.
     * Called in {@link UAirship} during takeoff.
     *
     * @hide
     */
    protected void init() {}

    /**
     * Tear down the manager.
     * Called in {@link UAirship} during land.
     *
     * @hide
     */
    protected void tearDown() {}
}
