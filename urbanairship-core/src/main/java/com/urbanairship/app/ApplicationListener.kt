/* Copyright Airship and Contributors */
package com.urbanairship.app

/**
 * Listener for application foreground and backgrounds.
 */
public interface ApplicationListener {

    /**
     * Called when the app is foregrounded.
     *
     * @param milliseconds Time in milliseconds when the foreground occurred.
     */
    public fun onForeground(milliseconds: Long)

    /**
     * Called when the app is backgrounded.
     *
     * @param milliseconds Time in milliseconds when the background occurred.
     */
    public fun onBackground(milliseconds: Long)
}
