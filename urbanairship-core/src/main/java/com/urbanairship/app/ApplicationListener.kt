/* Copyright Airship and Contributors */
package com.urbanairship.app

import java.time.Instant

/**
 * Listener for application foreground and backgrounds.
 */
public interface ApplicationListener {

    /**
     * Called when the app is foregrounded.
     *
     * @param timestamp The time when the foreground occurred.
     */
    public fun onForeground(timestamp: Instant)

    /**
     * Called when the app is backgrounded.
     *
     * @param timestamp The time when the background occurred.
     */
    public fun onBackground(timestamp: Instant)
}
