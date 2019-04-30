/* Copyright Airship and Contributors */

package com.urbanairship.app;

/**
 * Listener for application foreground and backgrounds.
 */
public interface ApplicationListener {

    /**
     * Called when the app is foregrounded.
     *
     * @param milliseconds Time in milliseconds when the foreground occurred.
     */
    void onForeground(long milliseconds);

    /**
     * Called when the app is backgrounded.
     *
     * @param milliseconds Time in milliseconds when the background occurred.
     */
    void onBackground(long milliseconds);

}
