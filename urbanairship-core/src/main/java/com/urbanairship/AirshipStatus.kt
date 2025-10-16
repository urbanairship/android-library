package com.urbanairship

/**
 * Represents the status of the Airship SDK.
 */
public enum class AirshipStatus {
    /**
     * `Airship.takeOff` has not been called. The SDK is not initialized.
     */
    TAKEOFF_NOT_CALLED,

    /**
     * `Airship.takeOff` has been called, but the SDK is still initializing.
     */
    TAKING_OFF,

    /**
     * The SDK is fully initialized and ready.
     */
    IS_FLYING
}
