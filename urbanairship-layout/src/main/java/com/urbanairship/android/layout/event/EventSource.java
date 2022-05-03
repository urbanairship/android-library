/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

/** Event source interface. */
public interface EventSource {
    /**
     * Adds an event listener.
     *
     * @param listener the {@code EventListener} to add.
     */
    void addListener(EventListener listener);

    /**
     * Removes an event listener.
     *
     * @param listener the {@code EventListener} to remove.
     */
    void removeListener(EventListener listener);

    /**
     * Sets an event listener, removing any previously set listeners.
     *
     * @param listener the {@code EventListener} to set.
     */
    void setListener(EventListener listener);
}
